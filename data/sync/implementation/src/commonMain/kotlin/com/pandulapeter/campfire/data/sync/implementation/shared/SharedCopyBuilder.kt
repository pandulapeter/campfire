/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.model.library.sharedCopyAttachments
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import org.koin.core.annotation.Single

/**
 * Lays out a shared link's copy from the library's files: the share's first copy (`SharedLinksRepositoryImpl.share`) and
 * every copy a run builds again ([SharedCopySync]) alike, so that a copy of unchanged files is the same bytes on every
 * device and at any time, and the first run after a share finds nothing to upload. Each passes the names it knows the
 * library by ([SharedCopyNames]).
 */
@Single
internal class SharedCopyBuilder(
    private val libraryFileLocalSource: LibraryFileLocalSource,
    private val contents: SharedCopyContents,
) {

    /**
     * The copy of the item [fileName] of [kind], holding [itemBytes], and for a setlist the songs it names ([songNames]).
     * Throws what reading a library file throws.
     */
    suspend fun build(
        kind: SharedLinkKind,
        fileName: String,
        itemBytes: ByteArray,
        songNames: List<String>?,
        names: SharedCopyNames,
    ): SharedCopyBuilt {
        // The item, then a setlist's songs once each under the setlist's spelling. They are read as text and written as
        // UTF-8, a byte order mark dropped, rather than as the bytes on disk, as an import reads them.
        val files = linkedMapOf(fileName to itemBytes.asCopyText())
        val songTexts = mutableListOf<ByteArray>()
        val songs = songNames?.map { name ->
            val bytes = libraryFileLocalSource.readLibraryFile(LibraryCollections.SONGS, names.localNameOf(LibraryCollections.SONGS, name))
            if (bytes != null && name !in files) {
                files[name] = bytes.asCopyText()
                songTexts += bytes
            }
            name to bytes?.let(::localContentHash)
        }
        // Then the pictures and attachments under images/, in the order of their names, where ChordPro looks for a
        // picture a song names by its bare file name. The pictures are read one at a time and no further than the limit:
        // a setlist of photos may name more than a phone has room for. The attachments (files no song of the copy shows)
        // are read in the order the songs name them, as far as their own budget goes, each one past it left out and let
        // go of at once (sharedCopyAttachments).
        val texts = if (kind == SharedLinkKind.SONG) listOf(itemBytes) else songTexts
        val intended = filesNamedBy(texts, names)
        val shown = texts.flatMapTo(hashSetOf()) { contents.pictureNamesOf(it) }
        val attachments = texts.flatMap { contents.attachmentNamesOf(it) }.filter { it in intended && it !in shown }
        val read = mutableMapOf<String, ByteArray>()
        var size = files.values.sumOf { it.size.toLong() }
        for (name in intended) {
            if (name in attachments) continue
            if (size > ImportLimits.MAX_SHARED_COPY_SIZE) break
            val bytes = readFile(name, names) ?: continue
            read[name] = bytes
            size += bytes.size
        }
        val isTooLarge = size > ImportLimits.MAX_SHARED_COPY_SIZE
        val leftOut = if (isTooLarge) {
            emptySet()
        } else {
            sharedCopyAttachments(names = attachments, onLeftOut = { read.remove(it) }) { name ->
                readFile(name, names)?.also { read[name] = it }?.size?.toLong()
            }.leftOut
        }
        // Its songs keep no line for an attachment the copy leaves out.
        if (leftOut.isNotEmpty()) {
            files.keys.filter { kind == SharedLinkKind.SONG || it != fileName }.forEach { name ->
                files[name] = contents.withoutAttachments(files.getValue(name), leftOut)
            }
        }
        intended.forEach { name -> read[name]?.let { files[LibraryFiles.ARCHIVE_FILES_FOLDER + name] = it } }
        val copy = if (isTooLarge) null else contents.pack(files)
        return SharedCopyBuilt(
            // What every version opening a link downloads, and what the copy and its files may hold in memory here at once.
            copy = copy?.takeIf { it.size <= ImportLimits.MAX_SHARED_COPY_SIZE + ImportLimits.MAX_SHARED_ATTACHMENTS_SIZE },
            songs = songs,
            // A copy too large is judged by what it was to hold, so that the next run finds nothing changed and reads no
            // picture again; one that fits by what it holds and what it left out for its size, so that a file it could
            // not read is tried again.
            images = if (isTooLarge) intended else intended.filter { it in read || it in leftOut },
            leftOut = leftOut,
        )
    }

    /**
     * The library's files [songs] name, pictures and attachments, by [names], in the order of their names: what a copy
     * carries besides its songs, and what its fingerprint names.
     */
    fun filesNamedBy(songs: List<ByteArray>, names: SharedCopyNames) = songs.flatMap { contents.libraryFileNamesOf(it) }.toSet()
        .filter { LibraryFiles.isLibraryFileName(it) && names.holdsFile(it) }
        .sorted()

    private suspend fun readFile(name: String, names: SharedCopyNames) =
        libraryFileLocalSource.readLibraryFile(LibraryCollections.FILES, names.localNameOf(LibraryCollections.FILES, name))

    /** A library text file as a copy holds it: read as the app reads text, then written as UTF-8. */
    private fun ByteArray.asCopyText() = decodeLibraryText().encodeToByteArray()
}
