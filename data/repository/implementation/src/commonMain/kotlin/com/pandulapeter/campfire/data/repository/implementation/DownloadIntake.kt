/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ReceivedArchive
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.source.local.api.ImportStagingLocalSource

/**
 * Where one streamed download's body goes, decided by its first bytes: a zip's into the staging folder, anything else
 * into memory within [ImportLimits.MAX_ARCHIVE_SIZE], exactly as a download read whole is. The import unpacks a staged
 * file as an archive, so only an archive may become one; a song, a document, or a host's error page is triaged from its
 * bytes as it always was.
 *
 * Not safe for concurrent use: the download writes it from one coroutine.
 */
internal class DownloadIntake(private val staging: ImportStagingLocalSource) {

    /** A body that is no archive passing what may be read into memory, which the download answers as too large. */
    class TooLargeForMemoryException : Exception("The download is no archive and too large to read into memory.")

    private var announcedSize: Long? = null
    private var head = ByteArray(0)
    private var received: ReceivedArchive? = null
    private var inMemory: MutableList<ByteArray>? = null
    private var inMemorySize = 0L

    fun open(announcedSize: Long?): suspend (ByteArray, Int, Int) -> Unit {
        this.announcedSize = announcedSize
        return ::write
    }

    /** The file the whole body came to, under [name]. */
    suspend fun finish(name: String): ImportDownload.File {
        if (received == null && inMemory == null) decide()
        received?.let { return ImportDownload.File(name = name, bytes = ByteArray(0), staged = it.finish()) }
        val bytes = ByteArray(inMemorySize.toInt())
        var offset = 0
        inMemory.orEmpty().forEach { chunk ->
            chunk.copyInto(bytes, offset)
            offset += chunk.size
        }
        return ImportDownload.File(name = name, bytes = bytes)
    }

    /** Deletes whatever was staged, since the download did not come to the file. Never throws. */
    suspend fun abandon() {
        received?.abandon()
        inMemory = null
    }

    private suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
        received?.let { return it.write(bytes, offset, length) }
        if (inMemory != null) return keep(bytes.copyOfRange(offset, offset + length))
        head += bytes.copyOfRange(offset, offset + length)
        if (head.size >= ZIP_SIGNATURE_SIZE) decide()
    }

    private suspend fun decide() {
        val first = head
        head = ByteArray(0)
        if (LibraryFiles.isZipArchive(first)) {
            received = staging.receiveArchive(announcedSize).also { it.write(first, 0, first.size) }
        } else {
            inMemory = mutableListOf()
            keep(first)
        }
    }

    private fun keep(chunk: ByteArray) {
        if (inMemorySize + chunk.size > ImportLimits.MAX_ARCHIVE_SIZE) throw TooLargeForMemoryException()
        inMemorySize += chunk.size
        inMemory?.add(chunk)
    }
}

/** How many bytes [LibraryFiles.isZipArchive] needs to tell. */
private const val ZIP_SIGNATURE_SIZE = 4
