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

/** A [ZipSource] over an archive already in memory: a Word document, a test's archive, a small download. */
class ByteArrayZipSource(private val bytes: ByteArray) : ZipSource {

    override val size get() = bytes.size.toLong()

    override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        if (position < 0 || length < 0 || position > bytes.size - length.toLong()) {
            throw ZipException("Truncated archive: $length byte(s) at offset $position are outside the ${bytes.size} byte input.")
        }
        bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + length)
    }
}
