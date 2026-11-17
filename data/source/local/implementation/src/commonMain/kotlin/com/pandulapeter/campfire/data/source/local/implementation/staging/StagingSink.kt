/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.staging

import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileSink
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * One file being staged by [ImportStaging], written front to back. Every write is given room in the ledger before it
 * is made, and an unpacked file is hashed over exactly the bytes written. Whatever ends it other than [finish] (a write
 * the storage refuses, the disk or the budget running out, a cancellation, the request deleted, or [abandon]) deletes
 * the partial file and gives its room back before the failure reaches the caller, so no partial file outlives its sink.
 *
 * Not safe for concurrent use: one coroutine writes it.
 */
internal class StagingSink(
    private val staging: ImportStaging,
    internal val request: StagingRequest,
    /** The name the file is written under: its final one for an archive, a partial one for an unpacked file. */
    internal val name: String,
    private val extension: String?,
    private val announcedSize: Long?,
    private var reserved: Long,
    private val fileSink: FileSink,
    private val digest: Sha256.Digest?,
) {

    private val isUnpacked get() = extension != null
    private var isOpen = true

    /** Whether the file was put in place whole by [take], so that there is no [fileSink] left to close. */
    private var isTaken = false

    /** How many bytes have been written so far. */
    var position = 0L
        private set

    suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
        check(isOpen && !isTaken) { "The staged file is closed." }
        try {
            check(staging.isLive(request)) { "The request was deleted." }
            val end = position + length
            if (!isUnpacked && end > ImportLimits.MAX_STAGED_ARCHIVE_SIZE) throw StagingRefusedException.TooLarge(ImportLimits.MAX_STAGED_ARCHIVE_SIZE)
            if (end > reserved) grow(end - reserved)
            fileSink.write(bytes, offset, length)
            digest?.update(bytes, offset, length)
            position = end
            staging.ledger.onWritten(request.id, length.toLong())
        } catch (exception: Throwable) {
            discard()
            throw exception
        }
    }

    /**
     * Fills the file, still empty, from [file], a platform's own handle of a file from outside (see
     * `FileStorage.takeInto`), [size] bytes long, given room for that before anything is copied. The empty file the sink
     * opened is let go of first, and the storage puts the whole file at its name. A received archive's only; its size is
     * read back from the storage, so that the ledger counts what is on disk.
     */
    suspend fun take(file: Any, size: Long, onProgress: (Long) -> Unit) {
        check(isOpen && !isTaken && position == 0L) { "Only an empty staged file can take a file whole." }
        try {
            check(staging.isLive(request)) { "The request was deleted." }
            if (!isUnpacked && size > ImportLimits.MAX_STAGED_ARCHIVE_SIZE) throw StagingRefusedException.TooLarge(ImportLimits.MAX_STAGED_ARCHIVE_SIZE)
            if (size > reserved) grow(size - reserved)
            isTaken = true
            fileSink.abandon()
            staging.takeInto(name = name, file = file, onProgress = onProgress)
            val taken = staging.sizeOf(name)
            if (!isUnpacked && taken > ImportLimits.MAX_STAGED_ARCHIVE_SIZE) throw StagingRefusedException.TooLarge(ImportLimits.MAX_STAGED_ARCHIVE_SIZE)
            if (taken > reserved) grow(taken - reserved)
            position = taken
            staging.ledger.onWritten(request.id, taken)
        } catch (exception: Throwable) {
            discard()
            throw exception
        }
    }

    /**
     * Closes the file and hands it out under its final name, giving back the room it was given and did not use. One that
     * cannot be closed or named is deleted, and the failure thrown.
     */
    suspend fun finish(): StagedFile {
        check(isOpen) { "The staged file is closed." }
        try {
            if (!isTaken) fileSink.close()
            isOpen = false
            staging.ledger.giveBack(request.id, reserved = reserved - position, written = 0, isUnpacked = isUnpacked)
            reserved = position
            return staging.complete(sink = this, size = position, extension = extension, sha256Hex = digest?.finishToHex())
        } catch (exception: Throwable) {
            discard()
            throw exception
        }
    }

    /** Deletes the partial file and gives its room back. Does nothing once the sink is finished or abandoned. */
    suspend fun abandon() {
        if (isOpen) discard()
    }

    /**
     * Room for [shortfall] more bytes: exactly that where the file stays within its announced size, otherwise a step at a
     * time, so that a file of unknown length does not ask the storage for its free space at every write. A step that does
     * not fit is tried again as only what this write needs.
     */
    private suspend fun grow(shortfall: Long) {
        val isAnnounced = announcedSize != null && position + shortfall <= announcedSize
        val step = if (isAnnounced) shortfall else maxOf(shortfall, GROWTH_STEP)
        val taken = try {
            staging.ledger.take(request.id, step, isUnpacked)
            step
        } catch (exception: StagingRefusedException) {
            if (step == shortfall) throw exception
            staging.ledger.take(request.id, shortfall, isUnpacked)
            shortfall
        }
        reserved += taken
    }

    private suspend fun discard() = withContext(NonCancellable) {
        val wasOpen = isOpen
        isOpen = false
        // A sink already closed has nothing left to abandon but its file, which only the staging can still delete.
        if (wasOpen && !isTaken) runCatching { fileSink.abandon() }
        staging.deleteQuietly(name)
        staging.ledger.giveBack(request.id, reserved = reserved, written = position, isUnpacked = isUnpacked)
        reserved = 0
        position = 0
    }
}

/** How much more room a file of unknown or overrun length is given at a time. */
private const val GROWTH_STEP = 8L shl 20
