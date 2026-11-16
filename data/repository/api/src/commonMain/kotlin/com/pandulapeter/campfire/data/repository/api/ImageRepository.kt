/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageSize
import kotlinx.coroutines.flow.StateFlow

/**
 * The pictures songs show (`{image: src=…}`): files of the library named by their content, kept while a song names
 * them, plus the pictures the editor's unsaved text names, which wait outside the library until Save.
 *
 * **A picture's name is its content**: the lowercase hex of its SHA-256 (16 digits, more where a different picture took
 * the shorter name) and its format's extension, so a picture is never edited, only added or deleted, and the same
 * picture in two songs or on two devices is one file. **The library holds only what a saved song names**: a picture no
 * song names is deleted once the library has been read whole, as long as nothing holds it (the editor's text,
 * [withImagesKept]) and every song file of the folder was read, since a song that could not be read may name it.
 */
interface ImageRepository {

    /**
     * Changes whenever a picture of either shelf is written or deleted, by sync too: whoever shows one that was missing
     * asks again.
     */
    val changes: StateFlow<Long>

    /**
     * The picture called [name], from the library or else the editor's draft, or null where neither holds it or it
     * cannot be read.
     */
    suspend fun loadImage(name: String): ByteArray?

    /** The size [loadImage]'s picture is shown at before scaling, read from its header; null as for [loadImage]. */
    suspend fun loadImageSize(name: String): ImageSize?

    /** The names of the library's pictures (never the draft's), sorted: what an export may carry. */
    suspend fun loadLibraryImageNames(): List<String>

    /**
     * What [LibraryImageWriter.addImage] would answer for [bytes] now, with nothing written: the name the library would
     * keep it under, or why it would refuse it. What an import plans with before anything is written; the write may
     * still come out under a longer name, should a different picture have taken this one in between.
     */
    suspend fun libraryNameOf(bytes: ByteArray): ImageIntake

    /**
     * Keeps [bytes] for the editor's unsaved text, outside the library, under the name its `{image}` directive is to
     * name it by. Refused when it is too large or not a picture every platform can show.
     */
    suspend fun addDraftImage(bytes: ByteArray): ImageIntake

    /**
     * The names the editor's open text names, held from the library's pruning (and the draft's) until the editor
     * reports another set; an empty set when it closes.
     */
    fun holdEditorImages(names: Set<String>)

    /** Deletes the draft's pictures, once its text is saved (what it named moved into the library) or discarded. */
    suspend fun clearDraftImages()

    /**
     * Runs [block] with the pruning held off, so that pictures it stores are not deleted before the songs naming them are
     * written: what an import, a Save, and the missing-picture placeholder's Add do.
     */
    suspend fun <T> withImagesKept(block: suspend (LibraryImageWriter) -> T): T

    /** Told about library pictures sync wrote or deleted, which changes nothing but what is shown. */
    fun onLibraryImagesChanged()
}

/** The writes of the library's pictures, only ever made inside [ImageRepository.withImagesKept]. */
interface LibraryImageWriter {

    /** Stores [bytes] in the library under its content name, refused as [ImageRepository.addDraftImage] refuses. */
    suspend fun addImage(bytes: ByteArray): ImageIntake

    /**
     * Moves the draft's pictures among [names] into the library, and answers the ones that had to take another name
     * there (a different picture having taken that one meanwhile), whose directives the caller rewrites before saving.
     * A name neither shelf holds is left alone: the song shows it as missing.
     */
    suspend fun moveDraftImages(names: Set<String>): Map<String, String>
}
