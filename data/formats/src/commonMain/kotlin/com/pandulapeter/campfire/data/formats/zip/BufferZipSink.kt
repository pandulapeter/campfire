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

/** A [ZipSink] into memory, for the in-memory [ZipWriter.write]. */
internal class BufferZipSink(initialCapacity: Int) : ZipSink {

    private val builder = ByteArrayBuilder(initialCapacity)

    override val position get() = builder.size.toLong()

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = builder.bytes(bytes, offset, length)

    fun build() = builder.build()
}
