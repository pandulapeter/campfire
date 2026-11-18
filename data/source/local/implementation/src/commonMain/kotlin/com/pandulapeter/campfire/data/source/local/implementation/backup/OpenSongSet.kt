/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.backup

import com.pandulapeter.campfire.data.formats.document.XmlElement
import com.pandulapeter.campfire.data.formats.document.parseXml
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocumentFormat
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistSongDocument

/**
 * Reads one set of OpenSong: the XML file OpenSong keeps each set in (without an extension, in its `Sets` folder), which
 * OpenSongApp exports as an `.osts`, backs up in an `.osbs` zip of them, and bundles with its songs in an `.ossb`.
 *
 * `<set name="…">` holds `<slide_groups>`, one `<slide_group>` per item in the order it is played. A song is
 * `type="song"`, named by its file name in OpenSong's song folders (`name`, which is its title as often as not) and the
 * folder it is in (`path`, `Hymns/Evening/`, empty for the songs folder itself). Every other type - a note, a custom
 * slide, a scripture passage, an image, a style - is a slide of the set's own, which a setlist has no place for.
 */
internal object OpenSongSet {

    /** One set: its title, its notes, its songs in order, and the names of the items that are no songs. */
    class Set(val title: String?, val notes: String?, val songs: List<Item>, val otherItemNames: List<String>)

    /** A song of a set: its name in OpenSong, and the folders [name] is in, outermost first. */
    class Item(val name: String, val folders: List<String>)

    /** Whether [text] is an XML document whose root element is a set, told without parsing it. */
    fun isSet(text: String) = rootName(text) == ROOT

    /** Whether [text] is an XML document whose root element is a song, told without parsing it. */
    fun isSong(text: String) = rootName(text) == SONG_ROOT

    /** The set [text] holds, or null where it is no OpenSong set. */
    fun read(text: String): Set? {
        val root = try {
            parseXml(text)
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: IllegalStateException) {
            return null
        }
        if (root.localName != ROOT) return null
        val groups = root.child("slide_groups")?.children.orEmpty().filter { it.localName == "slide_group" }
        val (songs, others) = groups.partition { it.attribute("type")?.trim().equals(SONG_TYPE, ignoreCase = true) }
        return Set(
            title = root.attribute("name")?.trim()?.ifEmpty { null },
            notes = root.child("notes")?.text?.trim()?.ifEmpty { null },
            songs = songs.mapNotNull { group ->
                val name = group.attribute("name")?.trim()?.ifEmpty { null } ?: return@mapNotNull null
                Item(name = name, folders = folders(group.attribute("path").orEmpty()))
            },
            otherItemNames = others.map { it.displayName },
        )
    }

    /**
     * The setlist [set] translates into, followed by what of it is left out, where anything is: one unread file naming
     * the set and its items that are no songs, which the import reports as skipped.
     *
     * [resolve] is the batch name of the song an item is, where the archive the set came in holds it. Any other item
     * points at a name of its own within the set, numbered where two items share one, and carries its title in
     * [ImportedFile.setlistSongTitles], for the import to find the song elsewhere.
     */
    fun files(set: Set, fallbackTitle: String, usedNames: BatchNames, resolve: (Item) -> String?): List<ImportedFile> {
        val title = set.title ?: fallbackTitle
        val resolved = set.songs.map(resolve)
        val setNames = BatchNames().apply { resolved.filterNotNull().forEach { used += it.lowercase() } }
        val titles = mutableMapOf<String, String>()
        val entries = set.songs.mapIndexed { index, item ->
            val file = resolved[index] ?: ForeignSongHeader.uniqueName(base = item.name, extension = LibraryFiles.SONG_EXTENSION, usedNames = setNames)
                .also { titles[it] = item.name }
            SetlistSongDocument(file = file)
        }
        val setlist = ImportedFile(
            name = ForeignSongHeader.uniqueName(base = title, extension = LibraryFiles.SETLIST_EXTENSION, usedNames = usedNames),
            bytes = SetlistDocumentFormat.encode(SetlistDocument(title = title, description = set.notes.orEmpty(), songs = entries)).encodeToByteArray(),
            setlistSongTitles = titles,
        )
        val leftOut = set.otherItemNames.takeIf { it.isNotEmpty() }?.let { names -> ImportedFile.unread("$title: ${names.joinToString(", ")}") }
        return listOfNotNull(setlist, leftOut)
    }

    /**
     * The setlist a set picked on its own translates into (see [files]), or null where [file] is no set: an `.osts`, or
     * OpenSong's own file without an extension. Its songs are found by title, as nothing beside it holds them.
     */
    fun filesOf(file: ImportedFile, text: String): List<ImportedFile>? {
        val set = read(text) ?: return null
        return files(set = set, fallbackTitle = fallbackTitleOf(file), usedNames = BatchNames()) { null }
    }

    /** A set without a name of its own is titled by its file, without OpenSongApp's `Category__` in front of it. */
    fun fallbackTitleOf(file: ImportedFile) = file.baseName.substringAfterLast("__").ifBlank { file.baseName }

    /** `Hymns/Evening/` as its folders; a backslash separates as well, since OpenSong for Windows may write one. */
    fun folders(path: String) = path.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }

    /** What a set's item that is no song is called: the title of a note or a custom slide, else the name it is filed under. */
    private val XmlElement.displayName
        get() = child("title")?.text?.trim()?.ifEmpty { null }
            ?: attribute("name")?.trim()?.ifEmpty { null }
            ?: attribute("type")?.trim()?.ifEmpty { null }
            ?: "slide_group"

    /** The name of the first element of [text], past its declaration, comments, and processing instructions. */
    private fun rootName(text: String) = ROOT_ELEMENT.find(text.take(HEAD_LENGTH))?.groupValues?.get(1)?.substringAfter(':')

    /** How far into a file [isSet] and [isSong] look: past any declaration and comment OpenSong writes before its root. */
    const val HEAD_LENGTH = 4_096

    private const val ROOT = "set"
    private const val SONG_ROOT = "song"
    private const val SONG_TYPE = "song"

    private val ROOT_ELEMENT = Regex("""^\uFEFF?\s*(?:(?:<\?[^>]*\?>|<!--[\s\S]*?-->)\s*)*<([A-Za-z_][\w:.-]*)""")
}
