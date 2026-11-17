/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation

import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.repository.api.ImportDownloadRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pictures by address: each of [pictures] answered as its file (or [ImportDownload.TooLarge] past the ceiling asked
 * with), any other address [ImportDownload.Unreachable]. Every address asked is kept in [asked], in the order the
 * calls reached it: an import plans its downloads in parallel on the worker dispatcher, so they are recorded under a lock.
 */
internal class FakeImportDownloadRepository(private val pictures: Map<String, ByteArray> = emptyMap()) : ImportDownloadRepository {

    val asked = mutableListOf<String>()

    private val recording = Mutex()

    override suspend fun download(url: String): ImportDownload = throw NotImplementedError("Not expected to be called by this test.")

    override suspend fun downloadPicture(url: String, maxSizeBytes: Long): ImportDownload {
        recording.withLock { asked += url }
        val bytes = pictures[url] ?: return ImportDownload.Unreachable
        return if (bytes.size > maxSizeBytes) ImportDownload.TooLarge else ImportDownload.File(name = url.substringAfterLast('/'), bytes = bytes)
    }
}
