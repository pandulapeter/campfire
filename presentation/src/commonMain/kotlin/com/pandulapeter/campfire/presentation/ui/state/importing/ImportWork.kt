/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.ImportBookkeeping
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.model.isOutOfSpace
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.presentation.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.time.TimeSource

/**
 * The import's work, which belongs to the app rather than to a view model, as a sync run does: the queue the batches
 * wait in, the preparation under way and the plan it comes to, and the Import write, all on [scope]. [ImportController]
 * is its view, deciding what the screens show of it and when; a view model cleared meanwhile (an Android activity
 * finished while the process goes on) takes none of this with it, and the next one's controller attaches to what it
 * left (`ImportController.startQueue`). A review waiting for the user is not work: it goes with its view model, and so
 * does a batch being read to join it.
 *
 * Touched only on the main thread, which [scope] and every view model's scope dispatch to.
 *
 * Public because the public view model's constructor takes it.
 */
@Single
class ImportWork(
    private val scope: ApplicationScope,
    /** Also what [ImportController] reads a shared link's earlier imports with. */
    internal val useCases: ImportUseCases,
) {

    /**
     * One preparation: its batch, whether the import screen was put up while it is read
     * (`ImportController.isReportedWhileRead`), and what it comes to.
     */
    internal class Pending(val request: ImportRequest, val isReviewed: Boolean, val outcome: Deferred<Preparation>)

    private val _state = MutableStateFlow(ImportWorkState.IDLE)

    /** What the import is doing, which is what has to go on whether or not anybody is looking. */
    val state: StateFlow<ImportWorkState> = _state.asStateFlow()

    /**
     * Every batch of files waiting to be imported, taken one at a time by the controller's consumer. Files are handed over
     * whenever the system or the user feels like it - a second archive dropped while the first is still being written, a
     * file opened with the app while a review is up - and an import can only run while no other one is, so what arrives
     * in the meantime waits here rather than being dropped. What arrives while a review is on screen joins it instead
     * where it can, see `ImportController.awaitImportSettled`.
     */
    internal val queue = Channel<ImportRequest>(Channel.UNLIMITED)

    /** Batches that arrived during a review they could not join, taken in their order before the [queue]'s next. */
    internal val held = ArrayDeque<ImportRequest>()

    /** The batches [queuedImportCount] counts, which [settle] lets go of once each. */
    private val counted = mutableSetOf<ImportRequest>()

    /**
     * How many batches have been put into [queue] and not yet settled, so that What's new can tell a file opened with the
     * app a moment ago, which no import has taken yet, from a launch with nothing to import.
     */
    internal val queuedImportCount = MutableStateFlow(0)

    /**
     * True while an import is running, which the bar along the top of the window shows (`WorkProgressBar`), and which a
     * preparation or a write left behind by a view model keeps up until the next one has taken it or it has ended.
     */
    internal val isImporting = MutableStateFlow(false)

    /** The phase of an import the user asked for and how far into it it is, which the import screen shows. */
    internal val progress = MutableStateFlow<ImportProgress?>(null)

    /** [progress] for the platforms that show the work in a notification beside [state] (`ImportNotificationText.kt`). */
    val currentProgress: StateFlow<ImportProgress?> = progress.asStateFlow()

    /**
     * The preparation under way, or finished with nobody yet to take what it came to: the one [cancel] ends, and the one
     * a view model left behind for the next. Let go of with [release] by the controller that took its outcome.
     */
    internal var preparation: Pending? = null
        private set

    /** Whether the user asked for [preparation] to stop, which a view model being cleared is told apart from. */
    internal var isPreparationCancelled = false
        private set

    /**
     * Counts the preparations [prepare] has started, so that one cancelled and still winding down never writes [state]
     * over the next one's.
     */
    private var preparationCount = 0

    /** Whether the Import write is running, see [write]. */
    internal val isWriting get() = _state.value == ImportWorkState.WRITING

    /** Puts [request] at the end of [queue], counted until it is settled. */
    internal fun enqueue(request: ImportRequest) {
        counted += request
        queuedImportCount.update { it + 1 }
        queue.trySend(request)
    }

    /**
     * Completes [request] as over, and stops counting it if it came through [queue]: once, however often it is called.
     * What it staged is deleted then, on [scope] so that no view model leaving takes the deletion with it: imported,
     * cancelled, or failed and dismissed, nothing staged outlives its batch in this process.
     */
    internal fun settle(request: ImportRequest) {
        if (counted.remove(request)) queuedImportCount.update { it - 1 }
        request.settled.complete(Unit)
        // A batch let go of before its preparation took its files holds them still: archives being received, and ones
        // already staged.
        val arrived = (request.source as? ImportSource.Files)?.files.orEmpty()
        val receptions = arrived.mapNotNull { it.receiving }
        request.stagedFiles += arrived.mapNotNull { it.staged }
        stagingDeletions.removeAll { it.isCompleted }
        if (receptions.isNotEmpty()) stagingDeletions += scope.launch { receptions.forEach { it.discard() } }
        if (request.stagedFiles.isNotEmpty()) {
            val staged = request.stagedFiles.toList()
            request.stagedFiles.clear()
            stagingDeletions += scope.launch { useCases.deleteImportStaging(staged) }
        }
    }

    /** The deletions [settle] started, which [settleBeforeExit] waits for. */
    private val stagingDeletions = mutableListOf<Job>()

    /**
     * The import's part of a desktop process ending, once the preparation it cancelled has ended and no write is running:
     * every batch not yet settled (a review or a failure on screen, one waiting in the queue, the cancelled one, whose
     * controller settles it only a dispatch later) is settled here, and what each staged is deleted before this returns.
     * The process ends right after, and a deletion merely launched would leave an archive of up to 16 GB in the staging
     * folder until a launch that may never come.
     */
    internal suspend fun settleBeforeExit() {
        if (_state.value != ImportWorkState.IDLE) return
        counted.toList().forEach(::settle)
        stagingDeletions.toList().joinAll()
    }

    /**
     * Starts reading [request] as a sibling of every view model's work, so that neither the user's Cancel nor a view
     * model being cleared ends anything but what it is meant to, and a failure inside it only reaches the controller
     * through the outcome.
     */
    internal fun prepare(request: ImportRequest, isReviewed: Boolean): Pending {
        isPreparationCancelled = false
        val count = ++preparationCount
        val interruption = CompletableDeferred<Unit>().also { preparationInterruption = it }
        val outcome = scope.async {
            // A child, so that the outcome of an interrupted preparation is only ready once it has cleaned up after
            // itself, as a cancelled one's is.
            val preparing = async { prepared(request = request, count = count) }
            val interrupting = launch {
                interruption.await()
                preparing.cancel()
            }
            try {
                preparing.await()
            } catch (exception: CancellationException) {
                ensureActive()
                if (!interruption.isCompleted) throw exception
                Preparation.Failed(ImportFailure.INTERRUPTED)
            } finally {
                interrupting.cancel()
            }
        }
        return Pending(request = request, isReviewed = isReviewed, outcome = outcome).also { preparation = it }
    }

    /** Forgets [pending] once its outcome has been taken or is not wanted, cancelling it where it is still running. */
    internal fun release(pending: Pending) {
        if (preparation === pending) preparation = null
        pending.outcome.cancel()
    }

    /**
     * The loading stage's Cancel, offered while the file is still being downloaded, read and compared. Nothing has been
     * written by then, so the library is exactly as it was; the controller that takes the outcome lets go of the screen,
     * and the queue goes on to its next batch. Once the plan is being written or reviewed there is no preparation left to
     * cancel, and a late call does nothing. Cancellation is noticed where the preparation yields, between files and songs.
     * The screen goes only once the preparation has ended, which awaiting a cancelled outcome waits for: what the
     * preparation staged and deletes as it ends (under `NonCancellable`, as `ImportStaging`'s sinks do) is gone first.
     */
    fun cancel() {
        val pending = preparation ?: return
        isPreparationCancelled = true
        pending.outcome.cancel()
    }

    /**
     * Ends a preparation because the platform stopped what kept the app working in the background (Android's daily
     * allowance for a data sync service, iOS 26's continued task expiring or stopped in the system's progress), rather
     * than through the import screen: the staging is deleted as by [cancel], but the import ends in
     * [ImportFailure.INTERRUPTED], which the import screen shows with Try again on the user's return. Like [cancel],
     * nothing once the write has started. iOS's plain background time running out is no reason: the app is only
     * suspended, the import paused until it is back (`ImportKeepAlive`).
     */
    fun interrupt() {
        preparationInterruption?.complete(Unit)
    }

    /** Completed by [interrupt] to end the latest preparation, see [prepare]. */
    private var preparationInterruption: CompletableDeferred<Unit>? = null

    /**
     * Writes what the review chose. Run on [scope] and never cancelled (the use case writes `NonCancellable` as well), so
     * a library is never left between a song and its files; a view model cleared while it runs only stops waiting for
     * it, and the flags that keep the next view model's queue waiting are let go of here once it ends.
     */
    internal suspend fun write(
        plan: ImportPlan,
        choices: ImportChoices,
        bookkeeping: ImportBookkeeping,
        onProgress: (ImportProgress) -> Unit,
    ): ImportResult {
        _state.value = ImportWorkState.WRITING
        val writing = scope.async {
            try {
                useCases.applyImport(plan = plan, choices = choices, bookkeeping = bookkeeping, onProgress = onProgress)
            } finally {
                _state.value = ImportWorkState.IDLE
            }
        }
        try {
            return writing.await()
        } catch (exception: CancellationException) {
            if (writing.isActive) scope.launch {
                writing.join()
                progress.value = null
                isImporting.value = false
            }
            throw exception
        }
    }

    /** The download, where the batch is an address or a shared link, and the reading and comparing, as one cancellable piece of work. */
    private suspend fun prepared(request: ImportRequest, count: Int): Preparation {
        val enter = { state: ImportWorkState -> if (count == preparationCount && !isWriting) _state.value = state }
        val throttle = ImportProgressThrottle(TimeSource.Monotonic) { progress.value = it }
        try {
            val arrived = when (val source = request.source) {
                is ImportSource.Files -> source.files
                is ImportSource.Address -> {
                    enter(ImportWorkState.DOWNLOADING)
                    when (val download = throttle.download(source.url, source.name)) {
                        is ImportDownload.File -> listOf(download.toImportedFile()).also {
                            // A read that fails after this is tried again on the same bytes, or the same staged copy,
                            // rather than downloaded again.
                            request.source = ImportSource.Files(it)
                        }

                        ImportDownload.TooLarge -> return Preparation.Failed(ImportFailure.TOO_LARGE)
                        ImportDownload.Missing -> return Preparation.Failed(ImportFailure.NOT_FOUND)
                        ImportDownload.Unreachable -> return Preparation.Failed(ImportFailure.UNREACHABLE)
                    }
                }

                is ImportSource.SharedLink -> source.file?.let(::listOf) ?: run {
                    enter(ImportWorkState.DOWNLOADING)
                    when (val download = throttle.download(source.link.downloadUrl, source.name).toSharedLinkDownload()) {
                        is SharedLinkDownload.Zip -> listOf(download.file).also {
                            // The title stays the screen's subtitle, and a read that fails after this reads the same bytes again.
                            request.source = source.copy(file = download.file)
                        }

                        is SharedLinkDownload.Failed -> return Preparation.Failed(download.reason)
                    }
                }
            }
            val files = received(request = request, files = arrived, enter = enter, throttle = throttle)
            request.stagedFiles += files.mapNotNull { it.staged }
            enter(ImportWorkState.UNPACKING)
            val plan = useCases.prepareImport(files) { if (request.shouldAnnounceResult) throttle.offer(it) }
            throttle.flush()
            request.stagedFiles += plan.stagedFiles
            return Preparation.Planned(plan)
        } finally {
            enter(ImportWorkState.IDLE)
        }
    }

    /**
     * [files] with every archive still being received ([ImportedFile.receiving], whose copy started as it arrived) taken
     * once it is whole, the batch's source changed to them, so that Try again reads the same staged copies: the
     * preparation's first phase, [ImportWorkState.RECEIVING], its bytes the [progress]. One that turns out too large or
     * unreadable is that file unread, as it would have been read whole; a disk too full for one fails the preparation
     * ([ImportFailure.NO_SPACE], with no Try again, since what it was read from is gone), the others left for the
     * batch's settling. Cancelled, the preparation discards every one
     * before it ends, so the loading screen goes only once nothing of them is left; [interrupt]ed, it leaves them to go on
     * in the app's scope for Try again to take, since what the platform stopped was only the preparation, and the batch's
     * settling discards them whichever way it ends.
     *
     * An archive the batch holds in memory ([isArchiveHeldInMemory]) is staged here too, so that the preparation unpacks
     * every archive from the staging folder and none in memory. Its source stays the bytes, which Try again stages anew;
     * a copy whose preparation ends before it is planned is deleted at once.
     */
    private suspend fun received(
        request: ImportRequest,
        files: List<ImportedFile>,
        enter: (ImportWorkState) -> Unit,
        throttle: ImportProgressThrottle,
    ): List<ImportedFile> {
        val receptions = files.mapNotNull { it.receiving }
        if (receptions.isEmpty() && files.none { it.isArchiveHeldInMemory }) return files
        // A copy out of memory takes no longer than the unpack's first moments, so it is shown as no phase of its own.
        if (receptions.isNotEmpty()) enter(ImportWorkState.RECEIVING)
        val fileName = request.source.name
        val totalBytes = receptions.map { it.announcedSize }.takeIf { sizes -> sizes.none { it == null } }?.sumOf { it ?: 0L }
        val stagedFromMemory = mutableListOf<StagedFile>()
        try {
            val received = coroutineScope {
                val reporting = if (receptions.isEmpty()) null else launch {
                    combine(receptions.map { it.receivedBytes }) { it.sum() }.collect { bytes ->
                        throttle.offer(ImportProgress(ImportProgress.Phase.RECEIVING, fileName = fileName, completedBytes = bytes, totalBytes = totalBytes))
                    }
                }
                files.map { file ->
                    val reception = file.receiving
                    when {
                        reception != null -> receivedFile(file.name) { reception.await() }
                        file.isArchiveHeldInMemory -> receivedFile(file.name) {
                            useCases.receiveImportArchive.stage(file.bytes).also { stagedFromMemory += it }
                        }
                        else -> file
                    }
                }.also {
                    reporting?.cancel()
                    throttle.flush()
                }
            }
            if (receptions.isNotEmpty()) request.source = ImportSource.Files(received)
            return received
        } catch (throwable: Throwable) {
            withContext(NonCancellable) {
                if (throwable is CancellationException && preparationInterruption?.isCompleted != true) receptions.forEach { it.discard() }
                if (stagedFromMemory.isNotEmpty()) useCases.deleteImportStaging(stagedFromMemory)
            }
            throw throwable
        }
    }

    private suspend fun receivedFile(name: String, receive: suspend () -> StagedFile): ImportedFile = try {
        ImportedFile(name = name, bytes = ByteArray(0), staged = receive())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: StagingRefusedException.TooLarge) {
        ImportedFile.unread(name, isTooLarge = true)
    } catch (exception: Exception) {
        if (exception.isOutOfSpace()) throw ArchiveNotReceivedException(exception)
        println("Could not receive \"$name\": ${exception.message}")
        ImportedFile.unread(name)
    }

    /**
     * Downloads [url], its bytes offered as [progress] against the length the host declared where it sent one (a shared
     * link's zip comes without), so the loading stage fills as a large archive arrives; [fileName] names it there.
     */
    private suspend fun ImportProgressThrottle.download(url: String, fileName: String?): ImportDownload {
        offer(ImportProgress(ImportProgress.Phase.DOWNLOADING, fileName = fileName))
        return useCases.downloadImportFile(url) { receivedBytes, totalBytes ->
            offer(ImportProgress(phase = ImportProgress.Phase.DOWNLOADING, fileName = fileName, completedBytes = receivedBytes, totalBytes = totalBytes))
        }.also { flush() }
    }

    private val ImportPlan.stagedFiles get() = songs.flatMap { it.images }.mapNotNull { (it.content as? IncomingContent.Staged)?.file }

    init {
        // What an earlier process left in the staging folder: a crash or a closed tab during an import. Once per process,
        // since the staging session is the process's, and launched as the holder is built, before any batch is queued.
        scope.launch { useCases.clearImportStaging() }
    }
}
