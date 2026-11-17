/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.zip

/**
 * Where an entry the zip reader reads goes as it is read, a slice at a time: memory for a song's text
 * ([ByteArrayEntrySink]), a file in the staging folder for a picture, an attachment, or an archive inside the archive.
 *
 * Every sink handed to the reader ends in exactly one of two ways. [close] means every byte arrived, the entry's size
 * and CRC-32 matched what the archive declared, and the SHA-256 given is of exactly the bytes written. [abandon] means it
 * did not, whatever the reason (a damaged stream, a size or checksum that does not match, a cancelled import, a failed
 * [write] or [close]): nothing written may be used, and a file sink deletes its partial file.
 */
interface EntrySink {

    /** Appends [length] bytes of [bytes] from [offset]; the array is reused once this returns. */
    suspend fun write(bytes: ByteArray, offset: Int, length: Int)

    /** Every byte was written and checked; [sha256Hex] is their SHA-256 in lowercase hex. */
    suspend fun close(sha256Hex: String)

    /**
     * Throws away what was written. Called under cancellation too, so it must not depend on the coroutine being active,
     * and it may follow a [close] that threw. What it throws is attached to the failure that led here, never thrown
     * instead of it.
     */
    suspend fun abandon()
}
