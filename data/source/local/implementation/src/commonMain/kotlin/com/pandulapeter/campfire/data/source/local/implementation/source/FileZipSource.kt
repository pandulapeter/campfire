/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.formats.zip.ZipSource
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileSource

/**
 * A staged archive read by the zip reader. The reader asks only for ranges within [size], so a read that comes back short
 * is a file that changed under it, a storage failure (which ends the walk) rather than a damaged archive.
 */
internal class FileZipSource(private val source: FileSource) : ZipSource {

    override val size get() = source.size

    override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        var done = 0
        while (done < length) {
            val read = source.read(position + done, buffer, offset + done, length - done)
            if (read <= 0) throw LibraryStorageException("The staged archive ended before the size it was opened with.")
            done += read
        }
    }
}
