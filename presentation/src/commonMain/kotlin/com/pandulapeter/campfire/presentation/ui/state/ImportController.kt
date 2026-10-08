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

import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.domain.api.useCases.DeleteLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.ImportFilesUseCase
import com.pandulapeter.campfire.domain.api.useCases.PrepareImportUseCase
import com.pandulapeter.campfire.domain.api.useCases.RememberDemoLibraryFilesUseCase
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.firstRun.DemoLibrary
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import com.pandulapeter.campfire.presentation.ui.screens.importReport.followingLibraryFileNames
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
 * Every import: the queue the batches wait in, the reading and comparing that decides what an import would do, the
 * question the import screen asks, the writing, and what is said about it afterwards. Also the library's deletion,
 * which the queue waits for.
 *
 * @param updateBackStack Changes the back stack, see `CampfireViewModel.updateBackStack`.
 * @param hasUnsavedEditorText Whether the editor holds text that is not written yet, which the import screen waits for.
 * @param openImportedSong Opens the one song the system handed over, see `CampfireViewModel.openImportedSong`.
 * @param launchFileTransfer See [com.pandulapeter.campfire.presentation.ui.screens.export.ExportController.launchFileTransfer].
 * @param isLeaving Whether the desktop process is on its way out, which starts no batch any more.
 */
internal class ImportController(
    private val scope: CoroutineScope,
    private val dialogHost: DialogHost,
    private val messageSink: MessageSink,
    private val backStack: List<CampfireDestination>,
    private val updateBackStack: (SnapshotStateList<CampfireDestination>.() -> Unit) -> Unit,
    private val songTexts: StateFlow<Map<String, String>>,
    private val editorDraft: StateFlow<SongContent?>,
    private val hasUnsavedEditorText: () -> Boolean,
    private val openImportedSong: (fileName: String) -> Unit,
    private val launchFileTransfer: (block: suspend () -> Unit) -> Job?,
    private val isLeaving: () -> Boolean,
    private val prepareImport: PrepareImportUseCase,
    private val importFiles: ImportFilesUseCase,
    private val rememberDemoLibraryFiles: RememberDemoLibraryFilesUseCase,
    private val deleteLibrary: DeleteLibraryUseCase,
) {

    /**
     * Open for good, since its field is part of the import screen's list rather than something the screen opens, and
     * emptied whenever that screen is left. Not restored, since the screen it belongs to never is, see [backStack].
     */
    val importReportSearch = SearchState(isInitiallyOpen = true)

    /** True while an import is running, which the screens that can start one show as a progress bar. */
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    /** True while [deleteLibrary] is deleting, which the import queue waits for, see [deleteLibrary]. */
    private val isDeletingLibrary = MutableStateFlow(false)

    /**
     * The phase of an import the user asked for and how far into it it is, shown by a dialog while nothing else is
     * reporting on it, and by the import screen while that is open.
     */
    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    /**
     * What [CampfireDestination.ImportReport] shows, which is everything about an import that went anywhere but the
     * one way that needs no more than a snackbar: a question about names that are taken, the import it decides on
     * being written, and what an import that left something out came to. Null while no import has anything to report,
     * and an import that has one keeps the next batch waiting until the screen is left, see [awaitImportSettled].
     */
    private val _importReport = MutableStateFlow<ImportReport?>(null)
    val importReport: StateFlow<ImportReport?> = _importReport.asStateFlow()

    /** Whether the last change to [backStack] left [CampfireDestination.ImportReport] on it, see [onImportReportLeft]. */
    private var isImportReportOnBackStack = false

    /**
     * Counts the reports [showImportReport] has put up, so that a waiting one is pushed only if no later one replaced
     * it. The value cannot be compared instead: [followReportedFileNames] rewrites a waiting report as the library moves.
     */
    private var importReportRequest = 0

    /**
     * The import that has been worked out but not carried out, waiting for the user to answer the question
     * [ImportReport.Review] asks. Not part of the screen, which holds only what it draws: this is the work, and it has
     * to outlive whichever screen the import was started from. It never outlives the question: see [onImportReportLeft].
     */
    private var pendingImport: PendingImport? = null

    /**
     * The reading and comparing half of the import being run, which [cancelImportPreparation] can end. Touched only on the
     * main thread, like [isPreparationCancelled]: [import] runs in [scope] and the dialog's click calls in there.
     */
    private var preparation: Deferred<ImportPlan>? = null

    /**
     * Whether the user asked for [preparation] to stop, which a cancellation of the view model itself is told apart from.
     */
    private var isPreparationCancelled = false

    /**
     * Every batch of files waiting to be imported, taken one at a time by the consumer launched in `init`. Files are
     * handed over whenever the system or the user feels like it - a second archive dropped while the first is still
     * being written, a file opened with the app while the conflicts question is up - and an import can only run while
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
        enqueueImport(files)
    }

    /**
     * Files the system handed over: opened with Campfire, shared to it, or dropped onto it. Where that turns out to be
     * one song, the song is what the user was after rather than the library it went into, so it is opened.
     */
    fun importFiles(files: List<ImportedFile>) {
        enqueueImport(files, shouldOpenSong = true)
    }

    /**
     * Puts a batch at the end of [importQueue] and returns what completes once it is over, its conflicts question
     * answered, the import that answer decided on written, and its result dismissed. An empty batch is not queued at all.
     */
    fun enqueueImport(
        files: List<ImportedFile>,
        shouldAnnounceResult: Boolean = true,
        shouldOpenSong: Boolean = false,
        isDemoLibrary: Boolean = false,
    ): CompletableDeferred<Unit> {
        val request = ImportRequest(
            files = files,
            shouldAnnounceResult = shouldAnnounceResult,
            shouldOpenSong = shouldOpenSong,
            isDemoLibrary = isDemoLibrary,
        )
        if (files.isEmpty()) {
            request.settled.complete(Unit)
        } else {
            _queuedImportCount.update { it + 1 }
            importQueue.trySend(request)
        }
        return request.settled
    }

    /**
     * Suspends until no import is running and none has anything left to report. A conflict leaves its question on
     * the import screen when [import] returns, and the import the answer decides on only starts after that; whatever
     * the screen shows is the one import it is about, so the next batch also waits for it to be left.
     */
    suspend fun awaitImportSettled() {
        combine(_importReport, _isImporting) { report, isImporting -> report != null || isImporting }.first { !it }
    }

    /**
     * The first half of an import only works out what it would do. Nothing is written until the plan turns out to
     * have nothing worth asking about, or until the user has answered the question it does raise - which is why the
     * plan is kept here rather than on the screen that asks: the answer can arrive long after the screen that started
     * this was left.
     */
    suspend fun import(request: ImportRequest) {
        // Only ever called by the consumer of importQueue, which waits for each import to settle before the next, and
        // by the first run's demo library before that consumer takes anything, so this holds by construction; it is
        // kept so that a second caller could not start an import over a running one.
        if (request.files.isEmpty() || _isImporting.value || pendingImport != null) return
        _isImporting.update { true }
        isPreparationCancelled = false
        // A sibling of the consumer under the scope's supervisor rather than a child of it, so that the user's Cancel
        // ends only the preparation and never the queue, and a failure inside it only reaches this through await().
        val files = request.files
        val deferred = scope.async { prepareImport(files) { if (request.shouldAnnounceResult) _importProgress.value = it } }
        preparation = deferred
        val plan = try {
            deferred.await()
        } catch (exception: CancellationException) {
            _importProgress.value = null
            _isImporting.value = false
            // The user's Cancel cancels only the deferred; the consumer itself still being active is what tells the two apart.
            if (isPreparationCancelled && currentCoroutineContext().isActive) return
            throw exception
        } catch (exception: Exception) {
            // Nothing has been written, so there is nothing to list either, and one line says all there is to say.
            println("Could not read the files to import: ${exception.message}")
            _importProgress.value = null
            if (request.shouldAnnounceResult) messageSink.sendMessage(Message.ImportFailed)
            _isImporting.value = false
            return
        } finally {
            preparation = null
            request.files = emptyList()
        }
        // A Cancel can land after the preparation finished but before this resumed (the resumption is dispatched to the
        // main thread, behind a click already queued there): it was still pressed while the dialog said "reading", so it wins.
        if (isPreparationCancelled) {
            _importProgress.value = null
            _isImporting.value = false
            return
        }
        if (plan.hasConflicts) {
            _importProgress.value = null
            pendingImport = PendingImport(plan = plan, request = request)
            // Nothing is happening while the question is on screen, and a progress bar under it would say otherwise.
            // The report, which is set first, is what keeps the next batch waiting from here on.
            showImportReport(ImportReport.Review(plan.summary))
            _isImporting.value = false
        } else {
            applyImportPlan(plan = plan, resolution = ImportConflictResolution.KEEP_BOTH, request = request, isReported = false)
        }
    }

    /**
     * The progress dialog's Cancel, offered while the files are still being read and compared. Nothing has been written
     * by then, so the library is exactly as it was and nothing is announced; the queue goes on to its next batch. Once
     * the plan is being written or a question is up there is no preparation left to cancel, and a late click does nothing.
     */
    fun cancelImportPreparation() {
        val deferred = preparation ?: return
        isPreparationCancelled = true
        deferred.cancel()
    }

    /**
     * The answer to [ImportReport.Review], which is the only thing that ever overwrites a library file. The import
     * screen stays where it is and shows the import being written, and then what it came to.
     */
    fun resolveImport(resolution: ImportConflictResolution) {
        val pending = pendingImport ?: return
        pendingImport = null
        // Claimed before the question goes away rather than once the import has started, so that nothing waiting for
        // the two of them to be over (see importDemoLibrary) sees a moment with neither.
        _isImporting.update { true }
        _importReport.value = ImportReport.Importing
        scope.launch { applyImportPlan(plan = pending.plan, resolution = resolution, request = pending.request, isReported = true) }
    }

    /**
     * Expects [isImporting] to have been claimed by the caller, which both of them do before anything can observe the gap.
     *
     * @param isReported Whether the import screen was put up for this import before it started writing, which is
     *   where its outcome goes for as long as that screen has not been left. Otherwise the outcome is a snackbar, unless
     *   something was left out or went wrong, which is what the import screen is put up for.
     */
    private suspend fun applyImportPlan(
        plan: ImportPlan,
        resolution: ImportConflictResolution,
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
                    resolution = resolution,
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
            val isReportShown = isReported && isImportReportOnBackStack
            when {
                // The one song the system handed over is what the user was after, and the question about its name has
                // been answered: the import screen gives way to it rather than reporting on one file.
                songToOpen != null -> {
                    // Let go of first, since a report that still says it is being written outlives its screen.
                    if (isReported) _importReport.value = null
                    if (isReportShown) closeImportReport()
                    openImportedSong(songToOpen)
                    if (request.shouldAnnounceResult) messageSink.sendMessage(Message.ImportFinished(result, hasDetails = false))
                }

                isReportShown -> _importReport.value = ImportReport.Finished(result)
                // Left while it was being written, which is a choice to hear about it the short way.
                isReported -> {
                    _importReport.value = null
                    messageSink.sendMessage(Message.ImportFinished(result, hasDetails = true))
                }

                !request.shouldAnnounceResult -> Unit
                result.isClean -> messageSink.sendMessage(Message.ImportFinished(result, hasDetails = plan.entryCount > 1))
                else -> showImportReport(ImportReport.Finished(result))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not import the files: ${exception.message}")
            _importProgress.value = null
            if (isReported && isImportReportOnBackStack) {
                _importReport.value = ImportReport.Finished(null)
            } else {
                if (isReported) _importReport.value = null
                if (request.shouldAnnounceResult) messageSink.sendMessage(Message.ImportFailed)
            }
        } finally {
            _importProgress.value = null
            _isImporting.update { false }
        }
    }

    /**
     * Sets [report] at once, which is what keeps the next batch waiting, and puts the import screen on top of whatever
     * is on screen once nothing else is in its way: a dialog, whose input would otherwise be left under the screen, or
     * an editor holding unsaved text, which is only ever asked about while it is on top (see [navigateBack]) and would
     * be one closed window away from being lost under it. Launched rather than awaited, so that the import that asked
     * can finish meanwhile.
     */
    private fun showImportReport(report: ImportReport) {
        val request = ++importReportRequest
        _importReport.value = report
        scope.launch {
            combine(dialogHost.visibleDialog, editorDraft, songTexts, snapshotFlow { backStack.toList() }) { dialog, _, _, stack ->
                dialog == null && !(hasUnsavedEditorText() && stack.any { it is CampfireDestination.SongEditor })
            }.first { it }
            if (importReportRequest == request && _importReport.value != null && !isImportReportOnBackStack) {
                updateBackStack { add(CampfireDestination.ImportReport) }
            }
        }
    }

    /** The snackbar's way into the import screen, for an outcome that did not need it but has more to it than one line. */
    fun openImportReport(result: ImportResult) {
        if (_importReport.value != null || _isImporting.value) return
        showImportReport(ImportReport.Finished(result))
    }

    /**
     * Keeps the import screen's result naming the files the library holds, so that coming back to it from a song opened
     * from its list and deleted or renamed there shows the library as it now is rather than a row that no longer opens.
     */
    fun followReportedFileNames(fileName: (String) -> String?) = _importReport.update { report ->
        if (report is ImportReport.Finished && report.result != null) ImportReport.Finished(report.result.followingLibraryFileNames(fileName)) else report
    }

    /** Takes the import screen off the back stack, with whatever is on top of it, see [onImportReportLeft]. */
    private fun closeImportReport() {
        val index = backStack.indexOf(CampfireDestination.ImportReport)
        if (index >= 0) updateBackStack { while (size > index) removeAt(lastIndex) }
    }

    /**
     * However the import screen went - Back, its Close, Done, or the stack rebuilt under it - a question it asked is
     * answered by cancelling, which leaves the library exactly as it was: the plan is what is thrown away. An import
     * that is being written carries on, and is reported with a snackbar once it is done (see [applyImportPlan]); what
     * a finished one came to has been seen.
     */
    private fun onImportReportLeft() {
        importReportSearch.textFieldState.clearText()
        when (_importReport.value) {
            is ImportReport.Review -> {
                pendingImport = null
                _importReport.value = null
            }

            ImportReport.Importing -> Unit
            is ImportReport.Finished, null -> _importReport.value = null
        }
    }

    /**
     * Only ever offered from the settings screen, whose back stack holds no song screen and no editor, so there is
     * nothing open on a file that is about to go.
     */
    fun deleteLibrary() = messageSink.launchLibraryChange {
        // An import writes against the library it planned for: one running, or one whose question is still open, would
        // either outlive the deletion in part or be applied to a library that is gone. The import queue waits for the
        // flag the other way round. Checked and set before the first suspension, as import claims its own flag.
        if (_isImporting.value || pendingImport != null || isDeletingLibrary.value) {
            return@launchLibraryChange messageSink.sendMessage(Message.OperationFailed)
        }
        isDeletingLibrary.value = true
        try {
            deleteLibrary.invoke()
        } finally {
            isDeletingLibrary.value = false
        }
    }

    /**
     * One batch in [importQueue].
     *
     * @param shouldAnnounceResult False for the import nobody asked for: the demo library planted on a first run is
     *   the library the user is about to be shown, and a progress dialog, a snackbar or a result counting the files
     *   of it would be the app reporting on something that, as far as anyone can tell, simply came with it.
     * @param shouldOpenSong True for files the system handed over, see [importFiles].
     * @param isDemoLibrary The files are [DemoLibrary]'s, and what of them is written is remembered for sync.
     * @param files Emptied by [import] once the preparation is over: the plan carries everything the rest of the import
     *   needs, while the request lives for as long as a conflicts question does, which would otherwise keep up to the
     *   whole selection's bytes reachable for nothing.
     */
    class ImportRequest(
        var files: List<ImportedFile>,
        val shouldAnnounceResult: Boolean,
        val shouldOpenSong: Boolean,
        val isDemoLibrary: Boolean = false,
        val settled: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    /**
     * Whether an import went the one way a snackbar is enough for: everything written, nothing left out and nothing
     * stopped. Files that were already in the library count as written, since nothing of them is lost.
     */
    private val ImportResult.isClean
        get() = !isFailed && skippedFileNames.isEmpty() && skippedConflictingFileNames.isEmpty() &&
            oversizedFileNames.isEmpty() && unreadableDocumentFileNames.isEmpty()

    /** How many files the plan has something to say about, which is what makes an import more than one file's. */
    private val ImportPlan.entryCount
        get() = songs.size + setlists.size + skippedFileNames.size + oversizedFileNames.size + unreadableDocumentFileNames.size

    /** An import waiting for the answer to [ImportReport.Review], see [pendingImport]. */
    private class PendingImport(
        val plan: ImportPlan,
        val request: ImportRequest,
    )

    /**
     * Takes the batches of [importQueue] one at a time, once [demoLibraryDecision] has completed, see
     * `CampfireViewModel.demoLibraryDecision`.
     */
    fun startQueue(demoLibraryDecision: Deferred<Unit>) = scope.launch {
        demoLibraryDecision.await()
        for (request in importQueue) {
            try {
                // A batch that arrived while the library was being deleted is planned against what the deletion
                // left, not against a library half gone. Checked again after every wait, so that nothing can start
                // a deletion between the check and import claiming the import flag.
                while (isDeletingLibrary.value) isDeletingLibrary.first { !it }
                // Checked after the deletion wait rather than before it, so that a batch that waited one out does not
                // start once the exit has begun. Skipping it still settles it in the finally below.
                if (isLeaving()) continue
                import(request)
                // The next batch waits for this one's conflicts question too: it would have nowhere to be asked.
                awaitImportSettled()
            } finally {
                // Here rather than in import, so that the count is honest whatever that did: returned after the
                // preparation was cancelled, failed, or left a question that was answered or abandoned.
                _queuedImportCount.update { it - 1 }
                request.settled.complete(Unit)
            }
        }
    }

    /** Notes whether the import screen is still on the back stack, and lets go of what it asked once it is not. */
    fun onBackStackChanged() {
        val hadImportReport = isImportReportOnBackStack
        isImportReportOnBackStack = backStack.any { it == CampfireDestination.ImportReport }
        if (hadImportReport && !isImportReportOnBackStack) onImportReportLeft()
    }
}
