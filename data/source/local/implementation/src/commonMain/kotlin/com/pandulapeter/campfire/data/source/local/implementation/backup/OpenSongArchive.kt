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

import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.library.decodeLibraryText

/**
 * The OpenSong sets of one archive and the songs they name, translated together: OpenSongApp's `.osbs` of sets, its
 * `.ossb` of one set and its songs, and OpenSong's own folders zipped up (`Sets/`, `Songs/<folder>/`).
 *
 * A set names a song by its file name and folder, which two songs of an archive may share apart from the folder (a
 * "River Song" in two folders), so each song here gets a batch name of its own and the set points at it: by folder and
 * name first, then by name alone, then by the song's title, a song the set has not taken yet before one it has. An
 * `.ossb` keeps only the innermost folder in its file name (`Evening__River Song.ost` for `Hymns/Evening/`), which a
 * folder matches from either end. What the archive does not hold is the import's to find (see [OpenSongSet.files]).
 */
internal object OpenSongArchive {

    /** An archive's entries with its sets and songs translated in place, and what of the sets was left out. */
    class Translated(val files: List<ImportedFile>, val leftOut: List<ImportedFile>)

    /**
     * [files] (read from the entries called [entryNames], in the same order) with every OpenSong set a setlist and every
     * OpenSong song a `.cho`, or null where the archive holds no set, whose songs are then imported one by one as ever.
     */
    fun read(entryNames: List<String>, files: List<ImportedFile>): Translated? {
        // Only the head of a file is decoded to tell what it is: most archives hold no set, and their texts are decoded
        // whole once more by whatever reads them next.
        val heads = files.map { file -> if (file.isOpenSongCandidate) file.bytes.copyOf(minOf(file.bytes.size, OpenSongSet.HEAD_LENGTH)).decodeLibraryText() else null }
        val setIndices = heads.indices.filter { heads[it]?.let(OpenSongSet::isSet) == true }
        if (setIndices.isEmpty()) return null
        val usedNames = BatchNames()
        val translated = files.toMutableList()
        val songs = heads.indices.mapNotNull { index ->
            if (heads[index]?.let(OpenSongSet::isSong) != true) return@mapNotNull null
            val text = files[index].bytes.decodeLibraryText()
            val path = OpenSongSet.folders(entryNames[index])
            val (folders, name) = pathOf(folders = path.dropLast(1), fileName = path.lastOrNull() ?: files[index].name)
            val song = OpenSongSong.read(text, fallbackTitle = name) ?: return@mapNotNull null
            val batchName = ForeignSongHeader.uniqueName(base = name, extension = LibraryFiles.SONG_EXTENSION, usedNames = usedNames)
            translated[index] = ImportedFile(name = batchName, bytes = song.encodeToByteArray())
            ArchiveSong(folders = folders.map(LibraryFiles::identityKey), name = name, title = TITLE.find(song)?.groupValues?.get(1)?.trim(), batchName = batchName)
        }
        val leftOut = mutableListOf<ImportedFile>()
        setIndices.forEach { index ->
            val set = OpenSongSet.read(files[index].bytes.decodeLibraryText()) ?: return@forEach
            val taken = mutableSetOf<ArchiveSong>()
            val setFiles = OpenSongSet.files(set = set, fallbackTitle = OpenSongSet.fallbackTitleOf(files[index]), usedNames = usedNames) { item ->
                songs.matching(item, taken)?.also { taken += it }?.batchName
            }
            translated[index] = setFiles.first()
            leftOut += setFiles.drop(1)
        }
        return Translated(files = translated, leftOut = leftOut)
    }

    /** The song [item] names, see the class. */
    private fun List<ArchiveSong>.matching(item: OpenSongSet.Item, taken: Set<ArchiveSong>): ArchiveSong? {
        val itemFolders = item.folders.map(LibraryFiles::identityKey)
        val itemName = LibraryFiles.identityKey(item.name)
        fun List<ArchiveSong>.best() = sortedWith(compareBy<ArchiveSong>({ it in taken }, { it.name != item.name })).firstOrNull()
        val named = filter { LibraryFiles.identityKey(it.name) == itemName }
        return named.filter { it.isIn(itemFolders) }.best()
            ?: named.best()
            ?: filter { song -> song.title?.let(LibraryFiles::identityKey) == itemName }.best()
    }

    /** Whether this song is in the folder [itemFolders] names, reading either path from its innermost folder. */
    private fun ArchiveSong.isIn(itemFolders: List<String>) =
        folders.takeLast(itemFolders.size) == itemFolders || (folders.isNotEmpty() && itemFolders.takeLast(folders.size) == folders)

    /** A song's folders and name: its entry's, or for an `.ossb`'s `Folder__Name.ost` the folder its name carries. */
    private fun pathOf(folders: List<String>, fileName: String): Pair<List<String>, String> {
        if (!fileName.endsWith(BUNDLED_SONG_EXTENSION, ignoreCase = true)) return folders to fileName.removeSuffix(XML_EXTENSION)
        val parts = fileName.dropLast(BUNDLED_SONG_EXTENSION.length).split(BUNDLED_FOLDER_SEPARATOR)
        return (folders + parts.dropLast(1)) to parts.last()
    }

    /** What may be an OpenSong document: a text kept for its bytes (OpenSong's own files), an `.osts`, or an `.xml`. */
    private val ImportedFile.isOpenSongCandidate
        get() = bytes.isNotEmpty() && (
            isPlainTextByContent || (LibraryFiles.FOREIGN_SETLIST_EXTENSIONS + XML_EXTENSION).any { name.endsWith(it, ignoreCase = true) }
            )

    private class ArchiveSong(val folders: List<String>, val name: String, val title: String?, val batchName: String)

    private const val BUNDLED_SONG_EXTENSION = ".ost"
    private const val BUNDLED_FOLDER_SEPARATOR = "__"
    private const val XML_EXTENSION = ".xml"

    /** The title [OpenSongSong] writes first into a song's header. */
    private val TITLE = Regex("""^\{title:\s*([^\}]*)\}""", RegexOption.MULTILINE)
}
