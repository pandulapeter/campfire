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
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.library.LibraryFileSizes
import kotlinx.coroutines.flow.StateFlow

/**
 * The pictures songs show (`{image: src=…}`) and the files attached to them (`{meta: attachment …}`): files of the
 * library named by their content, kept while a song names them (`libraryFilesNamedBy`), plus the ones the editor's
 * unsaved text names, which wait outside the library until Save. Named for pictures, which it held first; everything it
 * says of a picture's keeping holds for an attachment too.
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
     * What the library's files take up on disk, listed again after every [changes]; null until first listed. The totals
     * are never the draft's, which are not the library's until Save; `LibraryFileSizes.fileSizes` names both shelves'.
     */
    val librarySizes: StateFlow<LibraryFileSizes?>

    /**
     * The picture called [name], from the library or else the editor's draft, or null where neither holds it or it
     * cannot be read.
     */
    suspend fun loadImage(name: String): ByteArray?

    /** The size [loadImage]'s picture is shown at before scaling, read from its header; null as for [loadImage]. */
    suspend fun loadImageSize(name: String): ImageSize?

    /**
     * The size the picture [bytes] holds is shown at before scaling, read from its header as [loadImageSize] reads a kept
     * one's, or null where it is not a picture every platform can show: what a picture not kept anywhere yet (one an
     * import under review brings) is laid out by.
     */
    suspend fun readImageSize(bytes: ByteArray): ImageSize?

    /** The names of the library's files, pictures and attachments (never the draft's), sorted: what an export may carry. */
    suspend fun loadLibraryImageNames(): List<String>

    /** The size of each of the library's files, by name, from a listing that reads none of them. */
    suspend fun loadLibraryFileSizes(): Map<String, Long>

    /**
     * What [LibraryImageWriter.addImage] would answer for [content] now, with nothing written: the name the library would
     * keep it under, or why it would refuse it. What an import plans with before anything is written; the write may
     * still come out under a longer name, should a different picture have taken this one in between.
     *
     * A picture is judged by its first `ImageInfo.MAX_HEADER_BYTES` and its size alone, so a staged one
     * (`IncomingContent.Staged`) is never read whole, and named by the hash its staging took. Comparing it with a library
     * file under the same name reads both a slice at a time. A staged file that is gone throws
     * `StagedFileMissingException`.
     */
    suspend fun libraryNameOf(content: IncomingContent): ImageIntake

    /** [libraryNameOf] of a picture in memory. */
    suspend fun libraryNameOf(bytes: ByteArray): ImageIntake = libraryNameOf(IncomingContent.Bytes(bytes))

    /**
     * What [LibraryImageWriter.addAttachment] would answer for [content] arriving with [extension] now, with nothing
     * written, as [libraryNameOf] answers for a picture: judged by its size and [extension] alone.
     */
    suspend fun libraryNameOfAttachment(content: IncomingContent, extension: String): ImageIntake

    /** [libraryNameOfAttachment] of a file in memory. */
    suspend fun libraryNameOfAttachment(bytes: ByteArray, extension: String): ImageIntake =
        libraryNameOfAttachment(IncomingContent.Bytes(bytes), extension)

    /**
     * Keeps [bytes] for the editor's unsaved text, outside the library, under the name its `{image}` directive is to
     * name it by. Refused when it is too large or not a picture every platform can show.
     */
    suspend fun addDraftImage(bytes: ByteArray): ImageIntake

    /**
     * Keeps [bytes], a file to attach to a song, for the editor's unsaved text, outside the library, under the name its
     * attachment line is to name it by: its content and [extension], the one it arrived with, lowercased. Nothing is
     * read from its bytes; refused only where it is larger than `ImportLimits.MAX_ATTACHMENT_FILE_SIZE` or the extension
     * is not among `LibraryFiles.ATTACHMENT_EXTENSIONS`.
     */
    suspend fun addDraftAttachment(bytes: ByteArray, extension: String): ImageIntake

    /**
     * The names the editor's open text names, held from the library's pruning (and the draft's) until the editor
     * reports another set; an empty set when it closes.
     */
    fun holdEditorImages(names: Set<String>)

    /**
     * The draft files an open Attachments sheet picked and has not saved yet, held from every tidying of the draft shelf
     * ([clearDraftImages] included, which an editor closing elsewhere calls) until the sheet reports another set; an empty
     * set once no sheet is open. Each call replaces the last, and it returns once the hold is in place.
     */
    suspend fun holdPickedAttachments(names: Set<String>)

    /**
     * Deletes the draft's pictures, once its text is saved (what it named moved into the library) or discarded, all but
     * the ones an Attachments sheet holds ([holdPickedAttachments]).
     */
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

    /**
     * Stores [content] in the library under its content name, refused as [ImageRepository.addDraftImage] refuses. A staged
     * file is moved in (`ImageLocalSource.moveStagedFile`), never written over a library file, and copied from where it
     * went by a later call naming it under another name. A staged file that is gone throws (`StagedFileMissingException`,
     * or the storage's failure where it went between the look and the move), having written nothing.
     */
    suspend fun addImage(content: IncomingContent): ImageIntake

    /** [addImage] of a picture in memory. */
    suspend fun addImage(bytes: ByteArray): ImageIntake = addImage(IncomingContent.Bytes(bytes))

    /**
     * Stores [content] in the library as an attachment, named and refused as [ImageRepository.addDraftAttachment] says,
     * a staged one moved in as [addImage] moves one.
     */
    suspend fun addAttachment(content: IncomingContent, extension: String): ImageIntake

    /** [addAttachment] of a file in memory. */
    suspend fun addAttachment(bytes: ByteArray, extension: String): ImageIntake = addAttachment(IncomingContent.Bytes(bytes), extension)

    /**
     * Moves the draft's pictures and attachments among [names] into the library, and answers the ones that had to take another name
     * there (a different picture having taken that one meanwhile), whose directives the caller rewrites before saving.
     * A name neither shelf holds is left alone: the song shows it as missing.
     */
    suspend fun moveDraftImages(names: Set<String>): Map<String, String>

    /**
     * The names the editor's open text names (or, until it reports, its stored draft's): kept by the pruning, and by sync,
     * which decides its deletions of pictures inside [ImageRepository.withImagesKept] and must keep these too.
     */
    val heldNames: Set<String>
}
