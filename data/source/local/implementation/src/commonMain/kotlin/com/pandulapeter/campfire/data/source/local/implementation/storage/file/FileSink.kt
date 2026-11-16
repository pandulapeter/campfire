/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.file

/**
 * A file being written front to back by [FileStorage.openSink], for an export: nothing reads it before [close], so it
 * is written in place rather than atomically, and a failure throws `LibraryStorageException` and leaves the caller to
 * [abandon] it.
 */
interface FileSink {

    /** How many bytes have been written so far. */
    val position: Long

    suspend fun write(bytes: ByteArray, offset: Int, length: Int)

    /** Writes out what is buffered and closes the file, which is whole from then on. */
    suspend fun close()

    /** Closes the file and removes it, quietly: what is left of a write that failed or was cancelled. */
    suspend fun abandon()
}
