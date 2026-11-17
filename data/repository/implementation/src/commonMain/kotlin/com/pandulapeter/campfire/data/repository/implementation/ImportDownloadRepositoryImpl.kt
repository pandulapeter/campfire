/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportIntake
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.repository.api.ImportDownloadRepository
import com.pandulapeter.campfire.data.source.local.api.ImportStagingLocalSource
import com.pandulapeter.campfire.data.source.remote.api.ImportDownloadRemoteSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
internal class ImportDownloadRepositoryImpl(
    private val importDownloadRemoteSource: ImportDownloadRemoteSource,
    private val importStagingLocalSource: ImportStagingLocalSource,
) : ImportDownloadRepository {

    override suspend fun download(url: String, onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit) = if (ImportIntake.STAGES_ARCHIVES) {
        downloadIntoStaging(url = url, onProgress = onProgress)
    } else {
        importDownloadRemoteSource.download(url = url, maxSizeBytes = ImportLimits.MAX_ARCHIVE_SIZE)
    }

    /** The staged path of [download], whatever [ImportIntake.STAGES_ARCHIVES] says. */
    internal suspend fun downloadIntoStaging(url: String, onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit): ImportDownload {
        val intake = DownloadIntake(importStagingLocalSource)
        try {
            val download = importDownloadRemoteSource.download(
                url = url,
                maxSizeBytes = ImportLimits.MAX_STAGED_ARCHIVE_SIZE,
                openSink = intake::open,
                onProgress = onProgress,
            )
            if (download is ImportDownload.File) return intake.finish(download.name)
            withContext(NonCancellable) { intake.abandon() }
            return download
        } catch (exception: DownloadIntake.TooLargeForMemoryException) {
            withContext(NonCancellable) { intake.abandon() }
            return ImportDownload.TooLarge
        } catch (throwable: Throwable) {
            withContext(NonCancellable) { intake.abandon() }
            throw throwable
        }
    }

    override suspend fun downloadPicture(url: String, maxSizeBytes: Long) = importDownloadRemoteSource.downloadPicture(url = url, maxSizeBytes = maxSizeBytes)
}
