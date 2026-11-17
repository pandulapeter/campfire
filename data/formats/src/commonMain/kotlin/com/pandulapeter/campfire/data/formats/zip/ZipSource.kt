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
 * An archive read in pieces at any position, so that it never has to be held whole: a file in the staging folder, or a
 * [ByteArrayZipSource] for one already in memory. The reader walks the central directory and goes back to each entry's
 * data, so the bytes must not change while it is open.
 */
interface ZipSource {

    /** How many bytes the archive holds. */
    val size: Long

    /**
     * Fills [length] bytes of [buffer] from [offset] with the archive's bytes from [position], all of them, never a
     * short read. The reader checks every range against [size] before asking for it, so whatever a read throws (a file
     * gone, a read error) is not a damaged archive: it is not caught as one, and ends the whole walk.
     */
    suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int)
}
