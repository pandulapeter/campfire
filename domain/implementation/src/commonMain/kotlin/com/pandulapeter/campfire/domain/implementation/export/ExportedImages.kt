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

import com.pandulapeter.campfire.chordpro.edit.ChordProAttachments
import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.chordpro.edit.libraryFilesNamedBy
import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.model.library.sharedCopyAttachments
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
 * shows as a picture counts as a picture, whatever else names it, as the import reads it. A shared link's copy
 * ([isSharedCopy]) carries only the attachments [sharedCopyAttachments] picks, decided from every song it will hold
 * before the first is written ([decideSharedAttachments]), counts the rest ([leftOutAttachmentCount]), and writes its
 * songs without a line for them, as the sync run that rebuilds the copy does (`SharedCopySync`): both make the same bytes.
 */
internal class ExportedImages private constructor(
    private val imageRepository: ImageRepository,
    private val libraryImageNames: List<String>,
    private val isSharedCopy: Boolean,
) {

    private val held = libraryImageNames.toHashSet()
    private val named = mutableSetOf<String>()
    private val shown = mutableSetOf<String>()
    private val attached = linkedSetOf<String>()
    private var carried: Set<String>? = null
    private var leftOut = emptySet<String>()

    /** How many attachments the songs name that a shared link's copy leaves out for their size. */
    var leftOutAttachmentCount = 0
        private set

    /** What the pictures written so far add up to, which an import reads within a budget of its own. */
    var size = 0L
        private set

    /** What the attachments written so far add up to, which an import reads within another budget of its own. */
    var attachmentSize = 0L
        private set

    /**
     * Notes the library files [text] names (pictures and attachments), as it goes into the archive, and answers it as it
     * goes in: unchanged, but for a shared copy's lines of the attachments it leaves out.
     */
    fun named(text: String): String {
        named += libraryFilesNamedBy(text)
        shown += ChordProImages.referencedSources(text)
        attached += ChordProAttachments.referencedSources(text)
        return ChordProAttachments.withoutAttachments(text, leftOut)
    }

    /**
     * For a shared copy, decides which attachments it carries from the [texts] of every song it will hold, before the
     * first is written, so that [named] can leave out the lines of the others.
     */
    suspend fun decideSharedAttachments(texts: List<String>) {
        val shownByAny = texts.flatMapTo(mutableSetOf()) { ChordProImages.referencedSources(it) }
        carried = picked(texts.flatMap { ChordProAttachments.referencedSources(it) }.filter { it in held && it !in shownByAny })
    }

    /** Whether [text] names a file the library holds, which makes a song exported alone an archive. */
    fun isNamedBy(text: String) = libraryFilesNamedBy(text).any { it in held }

    /** The archive entries of the pictures, to come after every song's. */
    val entries: List<suspend () -> ArchiveEntry?> = libraryImageNames.map { name ->
        suspend {
            if (name !in named || (name !in shown && name !in carriedAttachments())) {
                null
            } else {
                imageRepository.loadImage(name)?.let { bytes ->
                    if (name in shown) size += bytes.size else attachmentSize += bytes.size
                    ArchiveEntry(name = "$FOLDER$name", bytes = bytes)
                }
            }
        }
    }

    /** The attachments the archive carries: every one the library holds, or for a shared copy those that fit. */
    private suspend fun carriedAttachments(): Set<String> = carried ?: picked(attached.filter { it in held && it !in shown }).also { carried = it }

    private suspend fun picked(candidates: List<String>): Set<String> = if (!isSharedCopy) {
        candidates.toSet()
    } else {
        val sizes = imageRepository.loadLibraryFileSizes()
        sharedCopyAttachments(names = candidates, sizeOf = sizes::get).also {
            leftOut = it.leftOut
            leftOutAttachmentCount = it.leftOut.size
        }.carried
    }

    companion object {

        /** The folder an archive keeps its pictures in. */
        const val FOLDER = "images/"

        /** @param isSharedCopy Whether the archive is a shared link's copy, which leaves out the attachments that do not fit. */
        suspend fun of(imageRepository: ImageRepository, isSharedCopy: Boolean) =
            ExportedImages(imageRepository, imageRepository.loadLibraryImageNames(), isSharedCopy)
    }
}
