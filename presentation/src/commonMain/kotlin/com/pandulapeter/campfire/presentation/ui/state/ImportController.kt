/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.data.model.domain.ImportChoices
import com.pandulapeter.campfire.data.model.domain.ImportDownload
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.domain.api.useCases.DeleteLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.DownloadImportFileUseCase
import com.pandulapeter.campfire.domain.api.useCases.ImportFilesUseCase
import com.pandulapeter.campfire.domain.api.useCases.PrepareImportUseCase
import com.pandulapeter.campfire.domain.api.useCases.RememberDemoLibraryFilesUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.firstRun.DemoLibrary
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportFailure
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import com.pandulapeter.campfire.presentation.ui.screens.importReport.replaceCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Every import: the queue the batches wait in, the download of a file from an address, the reading and comparing that
 * decides what an import would do, the writing, and what is said about it afterwards; what the import screen shows is
 * [ImportReportState]'s. Also the library's deletion, which stays here because deletion and import exclude each other
 * and both flags and both checks are only correct together, in one class whose state is touched on the main thread.
 *
 * @param openImportedSong Opens the one song the system handed over, see `CampfireViewModel.openImportedSong`.
 * @param launchFileTransfer See [com.pandulapeter.campfire.presentation.ui.screens.export.ExportController.launchFileTransfer].
 * @param isLeaving Whether the desktop process is on its way out, which starts no batch any more.
 */
internal class ImportController(
    private val scope: CoroutineScope,
    private val dialogHost: DialogHost,
    private val messageSink: MessageSink,
    private val backStack: List<CampfireDestination>,
    private val reportState: ImportReportState,
    private val openImportedSong: (fileName: String) -> Unit,
    private val launchFileTransfer: (block: suspend () -> Unit) -> Job?,
    private val isLeaving: () -> Boolean,
    private val downloadImportFile: DownloadImportFileUseCase,
    private val prepareImport: PrepareImportUseCase,
    private val importFiles: ImportFilesUseCase,
    private val rememberDemoLibraryFiles: RememberDemoLibraryFilesUseCase,
    private val deleteLibrary: DeleteLibraryUseCase,
) {

    /** True while an import is running, which the screens that can start one show as a progress bar. */
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    /** True while [deleteLibrary] is deleting, which the import queue waits for, see [deleteLibrary]. */
    private val isDeletingLibrary = MutableStateFlow(false)

    /** The phase of an import the user asked for and how far into it it is, which the import screen shows. */
    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    /**
     * The batch [ImportReportState.importReport] is about from the moment it is put up until it is written: what
     * [retryImport] reads again and what [resolveImport] writes the review's choices for. Not part of the screen, which
     * holds only what it draws: this is the work, and it has to outlive whichever screen the import was started from. It
     * never outlives the report, see [onImportReportLeft].
     */
    private var activeRequest: ImportRequest? = null

    /**
     * The reading and comparing half of the import being run, which [cancelImportPreparation] can end. Touched only on the
     * main thread, like [isPreparationCancelled]: [import] runs in [scope] and the screen's click calls in there.
     */
    private var preparation: Deferred<Preparation>? = null

    /**
     * Whether the user asked for [preparation] to stop, which a cancellation of the view model itself is told apart from.
     */
    private var isPreparationCancelled = false

    /**
     * Every batch of files waiting to be imported, taken one at a time by the consumer launched in `init`. Files are
     * handed over whenever the system or the user feels like it - a second archive dropped while the first is still
     * being written, a file opened with the app while a review is up - and an import can only run while
     * no other one is, so what arrives in the meantime waits here rather than being dropped.
     */
    private val importQueue = Channel<ImportRequest>(Channel.UNLIMITED)

    /**
     * How many batches have been put into [importQueue] and not yet settled, so that What's new can tell a file opened
     * with the app a moment ago, which no import has taken yet, from a launch with nothing to import.
     */
    private val _queuedImportCount = MutableStateFlow(0)
    val queuedImportCount: StateFlow<Int> = _queuedImportCount.asStateFlow()

    /**
     * The picker is handed in by the composable that has it, but the work runs here: picking a file takes as long as
     * the user takes, and the bottom sheet or menu the action was started from is gone well before that.
     */
    fun importFiles(filePicker: FilePicker) = launchFileTransfer {
        if (_isImporting.value) return@launchFileTransfer
        val files = try {
            filePicker.pickFiles()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not pick the files to import: ${exception.message}")
            messageSink.sendMessage(Message.ImportFailed)
            return@launchFileTransfer
        }
        enqueueImport(ImportSource.Files(files))
    }

    /**
     * Files the system handed over: opened with Campfire, shared to it, or dropped onto it. Where that turns out to be
     * one song, the song is what the user was after rather than the library it went into, so it is opened, and a single
     * song file is written without a review unless its name is taken, see [isReviewed].
     */
    fun importFiles(files: List<ImportedFile>) {
        enqueueImport(ImportSource.Files(files), shouldOpenSong = true)
    }

    /**
     * A file somebody shared as an address, downloaded once and reviewed like any other. A link to one song opens it,
     * as a file opened with the app does.
     */
    fun importFromAddress(url: String) {
        enqueueImport(ImportSource.Address(url), shouldOpenSong = true)
    }

    /**
     * Puts a batch at the end of [importQueue] and returns what completes once it is over: reviewed, the import the
     * review decided on written, and its result dismissed. An empty batch is not queued at all.
     *
     * @param shouldReview False for the demo library, whose contents are known and asked for by name: it is written at
     *   once, and only a name it finds taken puts the import screen up.
     */
    fun enqueueImport(
        source: ImportSource,
        shouldAnnounceResult: Boolean = true,
        shouldOpenSong: Boolean = false,
        shouldReview: Boolean = true,
        isDemoLibrary: Boolean = false,
    ): CompletableDeferred<Unit> {
        val request = ImportRequest(
            source = source,
            shouldAnnounceResult = shouldAnnounceResult,
            shouldOpenSong = shouldOpenSong,
            shouldReview = shouldReview,
            isDemoLibrary = isDemoLibrary,
        )
        if (source.isEmpty) {
            request.settled.complete(Unit)
        } else {
            _queuedImportCount.update { it + 1 }
            importQueue.trySend(request)
        }
        return request.settled
    }

    /**
     * Suspends until no import is running and none has anything left to report. A reviewed batch leaves its review on
     * the import screen when [import] returns, and the import the review decides on only starts after that; whatever
     * the screen shows is the one import it is about, so the next batch also waits for it to be left. The queue waits
     * for this before taking a batch as well as after it, since a result reopened from the snackbar's Details is a
     * report the queue did not start.
     */
    suspend fun awaitImportSettled() {
        combine(reportState.importReport, _isImporting) { report, isImporting -> report != null || isImporting }.first { !it }
    }

    /**
     * The first half of an import only works out what it would do. Nothing is written until the user has reviewed it
     * and pressed Import, or, for a batch that is not reviewed, until the plan turns out to have no name taken - which
     * is why the plan is kept here rather than on the screen: the answer can arrive long after the screen that started
     * this was left.
     */
    suspend fun import(request: ImportRequest) {
        // Only ever called by the consumer of importQueue, which waits for each import to settle before the next and
        // for any report on screen, and by the first run's demo library before that consumer takes anything, so this
        // holds by construction; it is kept so that a second caller could not start an import over a running one.
        if (request.source.isEmpty || _isImporting.value || activeRequest != null) return
        val isReviewed = request.isReviewed
        if (isReviewed) {
            activeRequest = request
            // Set at once, which is what keeps the queue and What's new waiting, and pushed once nothing is in the way.
            reportState.show(ImportReport.Loading(request.source.name))
        }
        prepare(request = request, isReviewed = isReviewed)
    }

    /** Downloads, reads and compares the batch of [request], and hands the plan on to the review or to the writing. */
    private suspend fun prepare(request: ImportRequest, isReviewed: Boolean) {
        _isImporting.update { true }
        isPreparationCancelled = false
        // A sibling of the consumer under the scope's supervisor rather than a child of it, so that the user's Cancel
        // ends only the preparation and never the queue, and a failure inside it only reaches this through await().
        val deferred = scope.async { prepare(request) }
        preparation = deferred
        val outcome = try {
            deferred.await()
        } catch (exception: CancellationException) {
            // The user's Cancel cancels only the deferred; the consumer itself still being active is what tells the two apart.
            if (isPreparationCancelled && currentCoroutineContext().isActive) return onPreparationCancelled(isReviewed)
            _importProgress.value = null
            _isImporting.value = false
            throw exception
        } catch (exception: Exception) {
            println("Could not read the files to import: ${exception.message}")
            Preparation.Failed(ImportFailure.UNREADABLE)
        } finally {
            preparation = null
        }
        // A Cancel can land after the preparation finished but before this resumed (the resumption is dispatched to the
        // main thread, behind a click already queued there): it was still pressed while the screen said "reading", so it wins.
        if (isPreparationCancelled) return onPreparationCancelled(isReviewed)
        _importProgress.value = null
        when (outcome) {
            is Preparation.Failed -> {
                _isImporting.value = false
                if (isReviewed) {
                    // The source is kept, so that Try again has something to read.
                    reportState.report = ImportReport.Failed(reason = outcome.reason, sourceName = request.source.name)
                } else {
                    // Nothing has been written, so there is nothing to list either, and one line says all there is to say.
                    if (request.shouldAnnounceResult) messageSink.sendMessage(Message.ImportFailed)
                }
            }

            is Preparation.Planned -> {
                // The plan carries everything the rest of the import needs, while the request lives for as long as the
                // review does, which would otherwise keep up to the whole selection's bytes reachable for nothing.
                request.source = ImportSource.Files(emptyList())
                val plan = outcome.plan
                if (isReviewed || plan.hasConflicts) {
                    activeRequest = request
                    // Nothing is happening while the review is on screen, and a progress bar under it would say otherwise.
                    // The report, which is set first, is what keeps the next batch waiting from here on.
                    val review = ImportReport.Review(plan = plan, choices = ImportChoices(), isDemoLibrary = request.isDemoLibrary)
                    if (isReviewed) reportState.report = review else reportState.show(review)
                    _isImporting.value = false
                } else {
                    applyImportPlan(plan = plan, choices = ImportChoices(), request = request, isReported = false)
                }
            }
        }
    }

    /** The download, where the batch is an address, and the reading and comparing, as one cancellable piece of work. */
    private suspend fun prepare(request: ImportRequest): Preparation {
        val files = when (val source = request.source) {
            is ImportSource.Files -> source.files
            is ImportSource.Address -> {
                _importProgress.value = ImportProgress(ImportProgress.Phase.DOWNLOADING, fileName = source.name)
                when (val download = downloadImportFile(source.url)) {
                    is ImportDownload.File -> listOf(ImportedFile(name = download.name, bytes = download.bytes)).also {
                        // A read that fails after this is tried again on the same bytes rather than downloaded again.
                        request.source = ImportSource.Files(it)
                    }

                    ImportDownload.TooLarge -> return Preparation.Failed(ImportFailure.TOO_LARGE)
                    ImportDownload.Missing -> return Preparation.Failed(ImportFailure.NOT_FOUND)
                    ImportDownload.Unreachable -> return Preparation.Failed(ImportFailure.UNREACHABLE)
                }
            }
        }
        return Preparation.Planned(prepareImport(files) { if (request.shouldAnnounceResult) _importProgress.value = it })
    }

    /** What a preparation the user cancelled leaves: nothing, the screen it was shown on included. */
    private fun onPreparationCancelled(isReviewed: Boolean) {
        _importProgress.value = null
        _isImporting.value = false
        if (isReviewed) {
            activeRequest = null
            // Let go of first, so that the screen leaving finds nothing left to cancel.
            if (reportState.report is ImportReport.Loading) reportState.report = null
            reportState.close()
        }
    }

    /**
     * The loading stage's Cancel, offered while the file is still being downloaded, read and compared. Nothing has been
     * written by then, so the library is exactly as it was and nothing is announced; the queue goes on to its next
     * batch. Once the plan is being written or reviewed there is no preparation left to cancel, and a late click does
     * nothing. Cancellation is noticed where the preparation yields, between files and songs, so a single huge document
     * being extracted is not interrupted until it is read.
     */
    fun cancelImportPreparation() {
        val deferred = preparation ?: return
        isPreparationCancelled = true
        deferred.cancel()
    }

    /** The failure stage's Try again: the same address downloaded again, or the same bytes read again. */
    fun retryImport() {
        val request = activeRequest ?: return
        if (reportState.report !is ImportReport.Failed) return
        reportState.report = ImportReport.Loading(request.source.name)
        _isImporting.update { true }
        scope.launch { prepare(request = request, isReviewed = true) }
    }

    /**
     * The review's Import. Writing over a library file is the one thing an import cannot take back, so where the choices
     * replace any, [DialogType.ConfirmImportReplace] asks first, and its confirmation is what calls [resolveImport].
     */
    fun confirmImport() {
        val review = reportState.report as? ImportReport.Review ?: return
        val count = replaceCount(review.plan, review.choices)
        if (count > 0) dialogHost.showDialog(DialogType.ConfirmImportReplace(count)) else resolveImport()
    }

    /**
     * Writes what the review chose, which is the only thing that ever overwrites a library file. The import screen
     * stays where it is and shows the import being written, and then what it came to.
     */
    fun resolveImport() {
        val review = reportState.report as? ImportReport.Review ?: return
        val request = activeRequest ?: return
        activeRequest = null
        // Claimed before the review goes away rather than once the import has started, so that nothing waiting for
        // the two of them to be over (see importDemoLibrary) sees a moment with neither.
        _isImporting.update { true }
        reportState.report = ImportReport.Importing
        scope.launch { applyImportPlan(plan = review.plan, choices = review.choices, request = request, isReported = true) }
    }

    /**
     * Expects [isImporting] to have been claimed by the caller, which both of them do before anything can observe the gap.
     *
     * @param isReported Whether the import screen was put up for this import before it started writing, which is
     *   where its outcome goes for as long as that screen has not been left, unless it went clean: the user has just
     *   read the list, and reading it again as a result is one tap more than it is worth. Otherwise the outcome is a
     *   snackbar, unless something was left out or went wrong, which is what the import screen is put up for.
     */
    private suspend fun applyImportPlan(
        plan: ImportPlan,
        choices: ImportChoices,
        request: ImportRequest,
        isReported: Boolean,
    ) {
        try {
            // Not cancellable once it has started writing: the view model going away with the Android activity is no
            // reason to leave an archive half imported - its setlists come after all of its songs - and the
            // repositories the files go into outlive it, so whatever screen comes back finds the whole import.
            val result = withContext(NonCancellable) {
                importFiles.invoke(
                    plan = plan,
                    choices = choices,
                ) { if (request.shouldAnnounceResult) _importProgress.value = it }
                    .also { result ->
                        if (request.isDemoLibrary) {
                            rememberDemoLibraryFiles(
                                songFileNames = DemoLibrary.songFileNamesWrittenBy(result),
                                setlistFileNames = DemoLibrary.setlistFileNamesWrittenBy(result),
                            )
                        }
                    }
            }
            _importProgress.value = null
            val songToOpen = songToOpen(plan = plan, result = result, shouldOpenSong = request.shouldOpenSong)
            val isReportShown = isReported && reportState.isOnBackStack
            when {
                // The one song the system handed over is what the user was after, and the review has been answered:
                // the import screen gives way to it rather than reporting on one file.
                songToOpen != null -> {
                    // Let go of first, since a report that still says it is being written outlives its screen.
                    if (isReported) reportState.report = null
                    if (isReportShown) reportState.close()
                    // A first run opens the onboarding over a file the app was opened with; the song follows it, as an
                    // import screen would, rather than going on top of its pages.
                    scope.launch {
                        snapshotFlow { backStack.none { it is CampfireDestination.Onboarding } }.first { it }
                        openImportedSong(songToOpen)
                    }
                    if (request.shouldAnnounceResult) messageSink.sendMessage(Message.ImportFinished(result, hasDetails = false))
                }

                isReportShown && result.isClean -> {
                    reportState.report = null
                    reportState.close()
                    if (request.shouldAnnounceResult) messageSink.sendMessage(Message.ImportFinished(result, hasDetails = true))
                }

                isReportShown -> reportState.report = ImportReport.Finished(result)
                // Left while it was being written, which is a choice to hear about it the short way.
                isReported -> {
                    reportState.report = null
                    messageSink.sendMessage(Message.ImportFinished(result, hasDetails = true))
                }

                !request.shouldAnnounceResult -> Unit
                result.isClean -> messageSink.sendMessage(Message.ImportFinished(result, hasDetails = plan.entryCount > 1))
                else -> reportState.show(ImportReport.Finished(result))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not import the files: ${exception.message}")
            _importProgress.value = null
            if (isReported && reportState.isOnBackStack) {
                reportState.report = ImportReport.Finished(null)
            } else {
                if (isReported) reportState.report = null
                if (request.shouldAnnounceResult) messageSink.sendMessage(Message.ImportFailed)
            }
        } finally {
            _importProgress.value = null
            _isImporting.update { false }
        }
    }

    /** The snackbar's way into the import screen, for an outcome that did not need it but has more to it than one line. */
    fun openImportReport(result: ImportResult) {
        if (reportState.report != null || _isImporting.value) return
        reportState.show(ImportReport.Finished(result))
    }

    /**
     * However the import screen went - Back, its Close, Done, or the stack rebuilt under it - an import that has not
     * started writing is dropped, which leaves the library exactly as it was: a preparation is cancelled, and a failure
     * or a review is thrown away with the request it was about. An import that is being written carries on, and is
     * reported with a snackbar once it is done (see [applyImportPlan]); what a finished one came to has been seen.
     */
    private fun onImportReportLeft() {
        when (reportState.report) {
            is ImportReport.Loading -> {
                reportState.report = null
                cancelImportPreparation()
            }

            is ImportReport.Failed, is ImportReport.Review -> {
                activeRequest = null
                reportState.report = null
            }

            ImportReport.Importing -> Unit
            is ImportReport.Finished, null -> reportState.report = null
        }
    }

    /**
     * Only ever offered from the settings screen, whose back stack holds no song screen and no editor, so there is
     * nothing open on a file that is about to go.
     */
    fun deleteLibrary() = messageSink.launchLibraryChange {
        // An import writes against the library it planned for: one running, or one still being read or reviewed, would
        // either outlive the deletion in part or be applied to a library that is gone. The import queue waits for the
        // flag the other way round. Checked and set before the first suspension, as import claims its own flag.
        if (_isImporting.value || activeRequest != null || isDeletingLibrary.value) {
            return@launchLibraryChange messageSink.sendMessage(Message.OperationFailed)
        }
        isDeletingLibrary.value = true
        try {
            deleteLibrary.invoke()
        } finally {
            isDeletingLibrary.value = false
        }
    }

    /** What [prepare] came to: a plan to review or write, or why there is none. */
    private sealed interface Preparation {
        class Planned(val plan: ImportPlan) : Preparation
        class Failed(val reason: ImportFailure) : Preparation
    }

    /**
     * Takes the batches of [importQueue] one at a time, once [demoLibraryDecision] has completed, see
     * `FirstRunController.demoLibraryDecision`.
     */
    fun startQueue(demoLibraryDecision: Deferred<Unit>) = scope.launch {
        demoLibraryDecision.await()
        for (request in importQueue) {
            try {
                // A result reopened from the snackbar's Details is on screen while the queue is idle; a batch taken now
                // would replace it in place, and leaving the screen would drop the new import with it. A batch that
                // arrived while the library was being deleted is planned against what the deletion left, not against a
                // library half gone. Both are checked again after every wait, since either can begin while the other is
                // waited out, and nothing can start a deletion or open a report between the last check and import
                // claiming the queue.
                do {
                    awaitImportSettled()
                    while (isDeletingLibrary.value) isDeletingLibrary.first { !it }
                } while (reportState.report != null || _isImporting.value)
                // Checked after the deletion wait rather than before it, so that a batch that waited one out does not
                // start once the exit has begun. Skipping it still settles it in the finally below.
                if (isLeaving()) continue
                import(request)
                // The next batch waits for this one's review too: it would have nowhere to be shown.
                awaitImportSettled()
            } finally {
                // Here rather than in import, so that the count is honest whatever that did: returned after the
                // preparation was cancelled, failed, or left a review that was answered or abandoned.
                _queuedImportCount.update { it - 1 }
                request.settled.complete(Unit)
            }
        }
    }

    /** Notes whether the import screen is still on the back stack, and lets go of what it asked once it is not. */
    fun onBackStackChanged() {
        if (reportState.onBackStackChanged()) onImportReportLeft()
    }
}
