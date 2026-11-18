/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalAtomicApi::class)

package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.model.importing.ImportIntake
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.ReceivedArchive
import com.pandulapeter.campfire.data.model.importing.ReceivingArchive
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.domain.api.importing.DeleteImportStagingUseCase
import com.pandulapeter.campfire.domain.api.importing.ReceiveImportArchiveUseCase
import com.pandulapeter.campfire.presentation.ApplicationScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.koin.mp.KoinPlatform
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Where the platforms' intake hands an archive that arrived (see [ImportIntake.decide]): its copy into the staging
 * folder starts at once, as a job of the app's own rather than of the screen or the batch, so that whatever lets the
 * app read the file is used while it is still valid and a batch waiting in the queue is received meanwhile. The
 * import's preparation awaits it (`ImportWork`), and the batch's settling or Cancel discards it.
 *
 * Public because the platforms' shells outside `:presentation` (Android's and iOS's "open with") hand files over too.
 */
@Single
class ArchiveReceiver(
    private val scope: ApplicationScope,
    private val receiveImportArchive: ReceiveImportArchiveUseCase,
    private val deleteImportStaging: DeleteImportStagingUseCase,
) {

    /**
     * [name] as an archive being received, [announcedSize] bytes long where the platform says (the room for it is then
     * reserved, and a disk too full for it refuses it, before anything is copied).
     *
     * @param close Lets go of whatever [copy] reads from (a stream opened as the file arrived), called once, from any
     *   thread, whether or not [copy] ever ran (the job may be discarded before it starts), and on a discard before the
     *   copy has stopped, so that a read blocked on a stalled pipe ends instead of holding the discard up.
     * @param copy Fills the archive given, front to back through [ReceivedArchive.write] or whole through
     *   [ReceivedArchive.take], on whatever thread it needs, throwing where the file cannot be read. Its writes and what
     *   it takes are counted as the bytes received.
     */
    fun receive(
        name: String,
        announcedSize: Long?,
        close: () -> Unit = {},
        copy: suspend (archive: ReceivedArchive) -> Unit,
    ): ImportedFile = ImportedFile(name = name, bytes = ByteArray(0), receiving = Reception(announcedSize, close, copy))

    private inner class Reception(
        override val announcedSize: Long?,
        private val close: () -> Unit,
        copy: suspend (archive: ReceivedArchive) -> Unit,
    ) : ReceivingArchive {

        private val isClosed = AtomicBoolean(false)
        private val isDiscarded = AtomicBoolean(false)

        private val _receivedBytes = MutableStateFlow(0L)
        override val receivedBytes = _receivedBytes.asStateFlow()

        private val job: Deferred<StagedFile> = scope.async {
            val archive = receiveImportArchive(announcedSize)
            try {
                copy(Counted(archive))
                archive.finish()
            } catch (throwable: Throwable) {
                archive.abandon()
                throw throwable
            }
        }.also { job -> job.invokeOnCompletion { closeOnce() } }

        override suspend fun await(): StagedFile = try {
            job.await().also { check(!isDiscarded.load()) { DISCARDED } }
        } catch (exception: Exception) {
            // A copy discarded rather than the caller cancelled, which the caller must not mistake for its own end, and a
            // copy that failed as it was being discarded, which is no failure of the file's.
            currentCoroutineContext().ensureActive()
            if (isDiscarded.load()) throw IllegalStateException(DISCARDED, exception)
            throw exception
        }

        override suspend fun discard() = withContext(NonCancellable) {
            isDiscarded.store(true)
            job.cancel()
            closeOnce()
            job.join()
            // A copy that had already finished leaves a whole archive, which only deleting its request takes away.
            if (!job.isCancelled) runCatching { job.await() }.getOrNull()?.let { deleteImportStaging(listOf(it)) }
            Unit
        }

        private fun closeOnce() {
            if (isClosed.compareAndSet(expectedValue = false, newValue = true)) runCatching(close)
        }

        private inner class Counted(private val archive: ReceivedArchive) : ReceivedArchive by archive {

            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                archive.write(bytes, offset, length)
                _receivedBytes.update { it + length }
            }

            override suspend fun take(file: Any, size: Long, onProgress: (receivedBytes: Long) -> Unit) = archive.take(file, size) { received ->
                _receivedBytes.value = received
                onProgress(received)
            }
        }
    }
}

private const val DISCARDED = "The archive was discarded."

/** The app's [ArchiveReceiver], for the platforms' intake outside a composition (the shells, the file pickers). */
fun archiveReceiver(): ArchiveReceiver = KoinPlatform.getKoin().get()
