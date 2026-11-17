/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.export

import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.chordpro.edit.libraryFilesNamedBy
import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.repository.api.ImageRepository

/**
 * The pictures an archive carries for the songs it holds, in `images/`, where ChordPro looks for a picture a song names by
 * its bare file name, so the archive opens the same in another ChordPro program, in another Campfire, and in this one.
 *
 * A song's text is only read when the writer reaches it, so which pictures go in is known only as the songs are
 * written: the archive gets one lazy entry per picture of the library ([libraryImageNames], sorted), after every song,
 * each leaving itself out unless a song written before it named it ([named]). So a picture goes in once however many
 * songs name it, only the library's own (a song may name one the library lacks), in an order that depends on nothing
 * but the names, which a shared copy built again from the same files relies on (`SharedCopySync` packs the same order).
 * Older versions skip `images/` unread, keeping the songs' directives as they are.
 *
 * The files attached to songs go there too, under their content names, so an import needs no other folder. One a song
 * shows as a picture counts as a picture, whatever else names it, as the import reads it.
 */
internal class ExportedImages private constructor(
    private val imageRepository: ImageRepository,
    private val libraryImageNames: List<String>,
) {

    private val held = libraryImageNames.toHashSet()
    private val named = mutableSetOf<String>()
    private val shown = mutableSetOf<String>()

    /** What the pictures written so far add up to, which an import reads within a budget of its own. */
    var size = 0L
        private set

    /** What the attachments written so far add up to, which an import reads within another budget of its own. */
    var attachmentSize = 0L
        private set

    /** Notes the library files [text] names (pictures and attachments), as it goes into the archive, and answers it unchanged. */
    fun named(text: String) = text.also {
        named += libraryFilesNamedBy(it)
        shown += ChordProImages.referencedSources(it)
    }

    /** Whether [text] names a file the library holds, which makes a song exported alone an archive. */
    fun isNamedBy(text: String) = libraryFilesNamedBy(text).any { it in held }

    /** The archive entries of the pictures, to come after every song's. */
    val entries: List<suspend () -> ArchiveEntry?> = libraryImageNames.map { name ->
        suspend {
            if (name !in named) {
                null
            } else {
                imageRepository.loadImage(name)?.let { bytes ->
                    if (name in shown) size += bytes.size else attachmentSize += bytes.size
                    ArchiveEntry(name = "$FOLDER$name", bytes = bytes)
                }
            }
        }
    }

    companion object {

        /** The folder an archive keeps its pictures in. */
        const val FOLDER = "images/"

        suspend fun of(imageRepository: ImageRepository) = ExportedImages(imageRepository, imageRepository.loadLibraryImageNames())
    }
}
