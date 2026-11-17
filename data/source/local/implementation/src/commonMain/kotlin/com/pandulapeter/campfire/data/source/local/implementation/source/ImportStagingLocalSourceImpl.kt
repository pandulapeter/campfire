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

import com.pandulapeter.campfire.data.model.importing.ReceivedArchive
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.source.local.api.ImportStagingLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.staging.ImportStaging
import com.pandulapeter.campfire.data.source.local.implementation.staging.StagingRequest
import com.pandulapeter.campfire.data.source.local.implementation.staging.StagingSink
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
internal class ImportStagingLocalSourceImpl(
    private val importStaging: ImportStaging,
) : ImportStagingLocalSource {

    override suspend fun deleteOtherSessions() = importStaging.deleteOtherSessions()

    override suspend fun isIntact(file: StagedFile) = importStaging.isIntact(file)

    override suspend fun deleteRequestsOf(files: Collection<StagedFile>) = importStaging.deleteRequestsOf(files)

    override suspend fun receiveArchive(announcedSize: Long?): ReceivedArchive {
        val request = importStaging.newRequest()
        val sink = endingRequestOnFailure(request) { importStaging.openArchive(request, announcedSize) }
        return StagedArchive(request = request, sink = sink)
    }

    /**
     * A received archive is its request's only file, so whatever ends the sink without a file ends the request too,
     * giving back all it reserved, rather than leaving an empty request for the next launch.
     */
    private inner class StagedArchive(
        private val request: StagingRequest,
        private val sink: StagingSink,
    ) : ReceivedArchive {

        private var isFinished = false

        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = endingRequestOnFailure(request) {
            sink.write(bytes, offset, length)
        }

        override suspend fun take(file: Any, size: Long, onProgress: (receivedBytes: Long) -> Unit) = endingRequestOnFailure(request) {
            sink.take(file = file, size = size, onProgress = onProgress)
        }

        override suspend fun finish(): StagedFile = endingRequestOnFailure(request) { sink.finish() }.also { isFinished = true }

        override suspend fun abandon() {
            if (isFinished) return
            withContext(NonCancellable) {
                sink.abandon()
                importStaging.deleteRequest(request)
            }
        }
    }

    private suspend fun <T> endingRequestOnFailure(request: StagingRequest, block: suspend () -> T): T = try {
        block()
    } catch (throwable: Throwable) {
        importStaging.deleteRequest(request)
        throw throwable
    }
}
