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
 * An [EntrySink] into memory, for what is read whole anyway (a song's text, a part of a Word document): [bytes] once it
 * is closed.
 *
 * An entry declaring more than [Inflater.MAX_DECLARED_ENTRY_SIZE] is refused as the sink is created, before anything is
 * allocated: an array that large is an allocation a phone's heap may not survive. The buffer starts at no more than
 * [Inflater.INITIAL_CAPACITY_LIMIT] whatever the entry declares, so a lying central directory costs only what the
 * stream really holds.
 */
class ByteArrayEntrySink(declaredSize: Long) : EntrySink {

    init {
        if (declaredSize < 0 || declaredSize > Inflater.MAX_DECLARED_ENTRY_SIZE) {
            throw ZipException("An entry of $declaredSize bytes is not read into memory.")
        }
    }

    private val builder = ByteArrayBuilder(minOf(declaredSize, Inflater.INITIAL_CAPACITY_LIMIT.toLong()).toInt())

    /** Whether the entry was read whole and checked, so that [bytes] holds it. */
    var isClosed = false
        private set

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = builder.bytes(bytes, offset, length)

    override suspend fun close(sha256Hex: String) {
        isClosed = true
    }

    override suspend fun abandon() {
        isClosed = false
        builder.clear()
    }

    /** What was written, once the entry was read whole and checked. */
    fun bytes(): ByteArray {
        check(isClosed) { "The entry was not read whole." }
        return builder.build()
    }
}
