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

/** An [EntrySink] that keeps what it was given and how it ended, and runs [onWrite] after each write. */
internal class RecordingEntrySink(private val onWrite: suspend () -> Unit = {}) : EntrySink {

    private val builder = ByteArrayBuilder()

    /** What [close] was called with, null where it was not. */
    var sha256Hex: String? = null
        private set

    var isAbandoned = false
        private set

    val bytes get() = builder.build()

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
        builder.bytes(bytes, offset, length)
        onWrite()
    }

    override suspend fun close(sha256Hex: String) {
        this.sha256Hex = sha256Hex
    }

    override suspend fun abandon() {
        isAbandoned = true
    }
}
