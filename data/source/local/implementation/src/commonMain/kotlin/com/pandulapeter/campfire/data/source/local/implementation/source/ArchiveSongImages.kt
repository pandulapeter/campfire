/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.data.formats.zip.EntryPolicy
import com.pandulapeter.campfire.data.formats.zip.UnreadZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipContent
import com.pandulapeter.campfire.data.formats.zip.ZipReader
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.library.decodeLibraryText

/**
 * The pictures the ChordPro songs of an archive name, read from the same archive in a second walk of it, after the
 * first has read the songs: a picture is read only for a song that names it, so the photos next to the songs of a
 * folder somebody zipped up cost nothing, and stay reported as skipped. Another app's song (OpenSong, OnSong, a JSON
 * export, ABC) brings none: those apps keep their pictures in ways of their own.
 *
 * Every picture is held to [ImportLimits.MAX_IMAGE_FILE_SIZE] and all of them together to what is left of the import's
 * picture budget, charged as the entries declare themselves before they are read, so a picture past either is reported
 * with its song rather than read. A picture named by several songs is read once.
 */
internal object ArchiveSongImages {

    /**
     * What [read] found: the pictures of each entry of the first walk, in its order (empty for one that is no song or
     * names none), which entries of the archive were taken for pictures (and so are not reported as skipped), and what
     * they cost the picture budget.
     */
    class Found(val imagesByEntry: List<List<ImportedImage>>, val takenEntryNames: Set<String>, val chargedSize: Long)

    /**
     * The pictures the songs among [content]'s entries name, out of [archive], within [maxSize] bytes. [isHidden] tells
     * the entries no song can mean, which the first walk left out unreported.
     */
    fun read(archive: ByteArray, content: ZipContent, maxSize: Long, isHidden: (String) -> Boolean): Found {
        val sources = content.entries.map { entry ->
            if (isHidden(entry.name) || LibraryFiles.SONG_EXTENSIONS.none { entry.name.endsWith(it, ignoreCase = true) }) {
                emptyList()
            } else {
                ChordProImages.referencedSources(entry.bytes.decodeLibraryText()).toList()
            }
        }
        if (sources.all { it.isEmpty() }) return Found(content.entries.map { emptyList() }, emptySet(), 0)
        val paths = ArchiveImagePaths((content.entries.map { it.name } + content.unread.map { it.name }).filterNot(isHidden))
        val resolved = content.entries.mapIndexed { index, entry -> sources[index].associateWith { paths.resolve(entry.name, it) } }
        val readFirst = content.entries.associate { it.name to it.bytes }
        val wanted = resolved.flatMap { it.values }.filterNotNull().filterNot(readFirst::containsKey).toSet()
        val pictures = if (wanted.isEmpty()) {
            null
        } else {
            ZipReader.read(
                archive = archive,
                maxTotalSize = maxSize,
                limitOf = { name, _ -> if (name in wanted) EntryPolicy.Read(ImportLimits.MAX_IMAGE_FILE_SIZE) else EntryPolicy.Skip },
            )
        }
        val bytesByName = readFirst + pictures?.entries.orEmpty().associate { it.name to it.bytes }
        val unreadByName = pictures?.unread.orEmpty().filter { it.name in wanted }.associate { it.name to it.reason }
        val images = resolved.map { bySource ->
            bySource.map { (source, name) ->
                val bytes = name?.let(bytesByName::get)
                ImportedImage(
                    source = source,
                    bytes = bytes,
                    problem = when {
                        bytes != null -> null
                        name == null -> ImportedImage.Problem.MISSING
                        unreadByName[name] == UnreadZipEntry.Reason.TOO_LARGE -> ImportedImage.Problem.TOO_LARGE
                        else -> ImportedImage.Problem.UNREADABLE
                    },
                )
            }
        }
        return Found(imagesByEntry = images, takenEntryNames = resolved.flatMap { it.values }.filterNotNull().toSet(), chargedSize = pictures?.chargedSize ?: 0)
    }
}
