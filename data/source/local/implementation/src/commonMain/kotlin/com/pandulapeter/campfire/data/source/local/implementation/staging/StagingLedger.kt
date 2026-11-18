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

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one disk budget every import of the app stages within. Each request reserves what it is about to write (as it is
 * announced, or as a write of unknown length grows) and gives it back as its files are deleted, so that two requests
 * measured at different moments (a share arriving during a review) cannot both fit and then fill the disk together.
 *
 * A reservation fits where the free space, less what [ImportLimits.minFreeSpaceFor] keeps free, holds it beside every
 * byte already reserved and not yet written. Bytes already written are not counted again: the free space the storage
 * reports has already lost them. Reservations are made one at a time, each asking the storage afresh, so no two are
 * measured against the same free space. Where the storage cannot tell its free space ([FileStorage.availableSpace] null:
 * Chrome's quota), nothing is refused up front, and a write the storage refuses fails the import as a full disk does.
 *
 * Where the space is a quota ([FileStorage.isSpaceAQuota], the web), the room of a file still being written is charged
 * twice: the browsers grow an open file's capacity by doubling, so a write may be refused with up to half of what is
 * left still free, and an early answer that counted bytes alone would promise room the engine then refuses. A closed
 * file holds only its size, so [onFinished] lets go of the rest, and a closed file is given back at its size
 * ([release]).
 */
internal class StagingLedger(private val fileStorage: FileStorage) {

    /**
     * What one request holds: [pool], reserved up front and not yet taken by a file; [reserved], everything it holds
     * ([pool] included); [written], the part of [reserved] already on disk; [unpacked], what its unpacked files hold,
     * written or not, which [ImportLimits.MAX_IMPORT_STAGED_SIZE] bounds. [pool] and [reserved] are charged bytes (see
     * the class: a closed file's are its size), [written] and [unpacked] bytes as they are.
     */
    private class Account {
        var pool = 0L
        var reserved = 0L
        var written = 0L
        var unpacked = 0L
    }

    private val mutex = Mutex()
    private val accounts = mutableMapOf<Long, Account>()

    /**
     * Reserves [bytes] for [request] up front, for the files it is about to stage (the declared sizes of an archive's
     * entries), which its sinks then take from before reserving more. Throws [StagingRefusedException.NoSpace] where they
     * do not fit, reserving nothing.
     */
    suspend fun reserve(request: Long, bytes: Long) = mutex.withLock {
        requireReservable(bytes)
        val account = accounts.getOrPut(request, ::Account)
        val charged = chargeOf(bytes)
        checkFits(account, charged)
        account.reserved += charged
        account.pool += charged
    }

    /**
     * Gives a file of [request] [bytes] more room, taken from what the request reserved up front and, past that,
     * reserved anew. An [isUnpacked] file counts against [ImportLimits.MAX_IMPORT_STAGED_SIZE] too. Throws a
     * [StagingRefusedException] where it does not fit, taking nothing.
     */
    suspend fun take(request: Long, bytes: Long, isUnpacked: Boolean) = mutex.withLock {
        requireReservable(bytes)
        val account = accounts.getOrPut(request, ::Account)
        if (isUnpacked && account.unpacked + bytes > ImportLimits.MAX_IMPORT_STAGED_SIZE) {
            throw StagingRefusedException.TooLarge(ImportLimits.MAX_IMPORT_STAGED_SIZE)
        }
        val charged = chargeOf(bytes)
        val pooled = minOf(account.pool, charged)
        val fresh = charged - pooled
        if (fresh > 0) checkFits(account, fresh)
        account.pool -= pooled
        account.reserved += fresh
        if (isUnpacked) account.unpacked += bytes
    }

    /** Notes that [bytes] a file of [request] had room for are now on disk. */
    suspend fun onWritten(request: Long, bytes: Long) = mutex.withLock {
        accounts[request]?.let { it.written += bytes }
        Unit
    }

    /**
     * Gives back [reserved] bytes a file of [request] still open held, of which [written] were on disk: a partial file
     * deleted, or the room a file was given and did not use.
     */
    suspend fun giveBack(request: Long, reserved: Long, written: Long, isUnpacked: Boolean) = mutex.withLock {
        val account = accounts[request] ?: return@withLock
        account.reserved = (account.reserved - chargeOf(reserved)).coerceAtLeast(account.pool)
        account.written = (account.written - written).coerceIn(0, account.reserved)
        if (isUnpacked) account.unpacked = (account.unpacked - reserved).coerceAtLeast(0)
    }

    /**
     * Notes that a file of [request] given [reserved] bytes of room was closed at [size] bytes: the room it did not use
     * is given back, and so is what its written bytes were charged beyond their size while it was open.
     */
    suspend fun onFinished(request: Long, reserved: Long, size: Long, isUnpacked: Boolean) = mutex.withLock {
        val account = accounts[request] ?: return@withLock
        account.reserved = (account.reserved - (chargeOf(reserved) - size)).coerceAtLeast(account.pool)
        if (isUnpacked) account.unpacked = (account.unpacked - (reserved - size)).coerceAtLeast(0)
    }

    /** Gives back a closed file of [request], [size] bytes on disk, once it is deleted or moved out of the folder. */
    suspend fun release(request: Long, size: Long, isUnpacked: Boolean) = mutex.withLock {
        val account = accounts[request] ?: return@withLock
        account.reserved = (account.reserved - size).coerceAtLeast(account.pool)
        account.written = (account.written - size).coerceIn(0, account.reserved)
        if (isUnpacked) account.unpacked = (account.unpacked - size).coerceAtLeast(0)
    }

    /**
     * Gives back what [request] reserved up front and no file took: the declared size of an entry the walk then left
     * unread (encrypted, damaged before its sink opened, past its limit) or never reached (a walk that failed).
     */
    suspend fun releasePool(request: Long) = mutex.withLock {
        val account = accounts[request] ?: return@withLock
        account.reserved -= account.pool
        account.pool = 0
    }

    /** Gives back everything [request] holds, once its files are deleted. */
    suspend fun close(request: Long) = mutex.withLock {
        accounts -= request
        Unit
    }

    /** What every request together has reserved and not yet written: what the free space must still make room for. */
    suspend fun outstanding() = mutex.withLock { accounts.values.sumOf { it.reserved - it.written } }

    /** Everything [request] holds, written or not, as charged (see the class). */
    suspend fun reservedBy(request: Long) = mutex.withLock { accounts[request]?.reserved ?: 0L }

    /**
     * No one reservation is larger than a received archive and what one import unpacks together, which also keeps every
     * sum of them far from overflowing, whatever a hostile archive declares.
     */
    private fun requireReservable(bytes: Long) {
        require(bytes >= 0) { "A reservation cannot be negative." }
        if (bytes > MAX_RESERVATION) throw StagingRefusedException.TooLarge(MAX_RESERVATION)
    }

    /** What [bytes] of a file cost the ledger, see the class. */
    private fun chargeOf(bytes: Long) = if (fileStorage.isSpaceAQuota) bytes * QUOTA_CHARGE else bytes

    private suspend fun checkFits(account: Account, bytes: Long) {
        val needed = account.reserved - account.written + bytes
        val margin = ImportLimits.minFreeSpaceFor(neededBytes = account.reserved + bytes, isQuota = fileStorage.isSpaceAQuota)
        val others = accounts.values.sumOf { it.reserved - it.written } - (account.reserved - account.written)
        val available = fileStorage.availableSpace(StorageDirectory.IMPORTS) ?: return
        if (available - margin - others < needed) throw StagingRefusedException.NoSpace(neededBytes = account.reserved + bytes + margin)
    }
}

/** How many bytes of a quota one byte of a staged file is charged, see [StagingLedger]. */
private const val QUOTA_CHARGE = 2L

private const val MAX_RESERVATION = ImportLimits.MAX_STAGED_ARCHIVE_SIZE + ImportLimits.MAX_IMPORT_STAGED_SIZE
