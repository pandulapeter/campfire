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

import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.chordpro.convert.PageTitle
import com.pandulapeter.campfire.data.model.importing.ImportBookkeeping
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.SharedLinkPart
import com.pandulapeter.campfire.data.model.importing.sharedLinkChoices
import com.pandulapeter.campfire.data.model.share.SharedLink
import com.pandulapeter.campfire.domain.api.library.DeleteLibraryUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.firstRun.DemoLibrary
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import com.pandulapeter.campfire.presentation.ui.state.SongLookup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Every import as the screens see it: what is said and shown of the batches [work] queues, reads, and writes (the work
 * itself belongs to the app, see [ImportWork]), from the review to what it came to afterwards; what the import screen
 * shows is [ImportReportState]'s. Also the library's deletion, which stays here because deletion and import exclude each
 * other and both flags and both checks are only correct together, in one class whose state is touched on the main thread.
 *
 * @param openLibraryItem Opens the library's own song or setlist that a batch turned out to be, see [LibraryItemToOpen].
 * @param loadSongText Reads the text of a song just written, which the preview it was imported from becomes the screen of.
 * @param openNewSongEditor Opens the editor on a pasted or shared text that is one new song, or on pictures that arrived
 *   alone, answering whether it did, see `EditorSession.openNewSongEditor`.
 * @param areSongsEnabled Whether the app has songs to open, which it does not with the Songs switch off: a file or a
 *   text sent to the app is then reviewed as any batch is, with nothing opened and no editor started on it.
 * @param songLookup The library as the screens look songs up in it, which the song to open is waited for in.
 * @param launchFileTransfer See [com.pandulapeter.campfire.presentation.ui.screens.export.ExportController.launchFileTransfer].
 * @param isLeaving Whether the desktop process is on its way out, which starts no batch any more.
 */
internal class ImportController(
    private val scope: CoroutineScope,
    private val dialogHost: DialogHost,
    private val messageSink: MessageSink,
    private val backStack: List<CampfireDestination>,
    private val reportState: ImportReportState,
    private val openLibraryItem: (LibraryItemToOpen) -> Unit,
    private val loadSongText: suspend (fileName: String) -> Unit,
    private val openNewSongEditor: suspend (song: ImportPlan.SongEntry, suggestion: PageTitle?) -> Boolean,
    private val areSongsEnabled: () -> Boolean,
    private val songLookup: Flow<SongLookup>,
    private val launchFileTransfer: (block: suspend () -> Unit) -> Job?,
    private val isLeaving: () -> Boolean,
    private val work: ImportWork,
    private val deleteLibrary: DeleteLibraryUseCase,
) {

    /** See [ImportWork.isImporting]. */
    private val _isImporting = work.isImporting
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    /** True while [deleteLibrary] is deleting, which the import queue waits for, see [deleteLibrary]. */
    private val isDeletingLibrary = MutableStateFlow(false)

    /** See [ImportWork.progress]. */
    private val _importProgress = work.progress
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    /**
     * The batch [ImportReportState.importReport] is about from the moment it is put up until it is written: what
     * [retryImport] reads again and what [resolveImport] writes the review's choices for. Not part of the screen, which
     * holds only what it draws: this is the work, and it has to outlive whichever screen the import was started from. It
     * never outlives the report, see [onImportReportLeft].
     */
    private var activeRequest: ImportRequest? = null

    /** The shared links among the batches of the review on screen, and which of its entries each brought, see [addToReview]. */
    private var sharedLinkParts = emptyList<SharedLinkPart>()

    /**
     * True while a batch that arrived during the review is read to be added to it (see [addToReview]), which the import
     * bar along the top of the window shows, the import screen holding Import back until the list is whole.
     */
    private val _isAddingToReview = MutableStateFlow(false)
    val isAddingToReview: StateFlow<Boolean> = _isAddingToReview.asStateFlow()

    /** See [ImportWork.queuedImportCount]. */
    val queuedImportCount: StateFlow<Int> = work.queuedImportCount.asStateFlow()

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
     * one song, the song is what the user was after rather than the library it went into: it is previewed, as one from
     * anywhere is (see [isPreviewed]), and one the library already holds is opened.
     *
     * @return Completed once the batch has settled, see [enqueueImport].
     */
    fun importFiles(files: List<ImportedFile>) = enqueueImport(ImportSource.Files(files), shouldOpenSong = true)

    /** A file shared as an address, downloaded once and reviewed like any other, previewed where it is one song. */
    fun importFromAddress(url: String) = enqueueImport(ImportSource.Address(url), shouldOpenSong = true)

    /**
     * A song or setlist shared as a Campfire link, however it arrived: opened from the system, pasted, dropped, or shared
     * as text. It is downloaded and always reviewed, with the names an earlier import of the same link wrote here
     * defaulting to Replace (see [sharedLinkChoices]); a link to one song is previewed rather than listed. A link from a newer
     * version of Campfire is answered with a sentence and queues nothing.
     *
     * @return Completed once the link has settled, see [enqueueImport].
     */
    fun importSharedLink(link: SharedLink): CompletableDeferred<Unit> = when (link) {
        is SharedLink.Readable -> enqueueImport(ImportSource.SharedLink(link), shouldOpenSong = true, sharedLinkFileId = link.fileId)
        is SharedLink.FromNewerVersion -> {
            messageSink.sendMessage(Message.SharedLinkFromNewerVersion)
            CompletableDeferred(Unit)
        }
    }

    /**
     * Puts a batch at the end of [ImportWork.queue] and returns what completes once it is over: reviewed, the import the
     * review decided on written, and its result dismissed, or its new song open in the editor, whose draft holds it from
     * then on. An empty batch is not queued at all.
     *
     * @param shouldReview False for the demo library, whose contents are known and asked for by name: it is written at
     *   once, and only a name it finds taken puts the import screen up.
     * @param isNewSongFromText True for a text pasted, dropped or shared as a new song: where it turns out to be one song
     *   the library does not hold, nothing is written and the editor opens on it instead (see [newSongOf]); one the
     *   library holds is opened; anything else - a songbook pasted whole - is the import it would be as a file.
     * @param suggestion The title and artist a shared text's subject suggests, which the editor opened on it fills in for
     *   what the text does not declare.
     * @param sharedLinkFileId See [ImportRequest.sharedLinkFileId].
     */
    fun enqueueImport(
        source: ImportSource,
        shouldAnnounceResult: Boolean = true,
        shouldOpenSong: Boolean = false,
        shouldReview: Boolean = true,
        isDemoLibrary: Boolean = false,
        isNewSongFromText: Boolean = false,
        suggestion: PageTitle? = null,
        sharedLinkFileId: String? = null,
    ): CompletableDeferred<Unit> {
        val request = ImportRequest(
            source = source,
            shouldAnnounceResult = shouldAnnounceResult,
            shouldOpenSong = shouldOpenSong,
            shouldReview = shouldReview,
            isDemoLibrary = isDemoLibrary,
            isNewSongFromText = isNewSongFromText,
            suggestion = suggestion,
            sharedLinkFileId = sharedLinkFileId,
        )
        if (source.isEmpty) {
            request.settled.complete(Unit)
        } else {
            work.enqueue(request)
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
     * [awaitImportSettled] for the queue, which takes every batch arriving meanwhile: one that can join the review on
     * screen ([canJoinReview]) is added to it and settled with it ([joined]), since a second review waiting behind the
     * first is a second screen for what the user dropped onto the first; the rest are [held] for after it, in order. A
     * batch that could join waits while [DialogType.ConfirmImportReplace] is open, and is held instead if Replace
     * starts writing the review.
     */
    private suspend fun awaitImportSettled(joined: MutableList<ImportRequest>, held: ArrayDeque<ImportRequest>) = coroutineScope {
        val settled = async { awaitImportSettled() }
        while (true) {
            // Biased towards the first clause, so that a batch arriving as the review is left is held rather than added.
            val next = select<ImportRequest?> {
                settled.onAwait { null }
                work.queue.onReceiveCatching { it.getOrNull() }
            } ?: break
            // Replace confirms the review as it stands: a batch joining under the dialog would add replacements nobody
            // confirmed, or make the confirmation do nothing while it is read. Checked again after every wait, with
            // nothing suspending between the last check and addToReview marking the batch as being added.
            while (canJoinReview(next) && dialogHost.visibleDialog.value is DialogType.ConfirmImportReplace) {
                combine(dialogHost.visibleDialog, reportState.importReport) { dialog, _ ->
                    dialog !is DialogType.ConfirmImportReplace || !canJoinReview(next)
                }.first { it }
            }
            if (canJoinReview(next)) {
                joined += next
                addToReview(next)
            } else {
                held += next
            }
        }
    }

    /**
     * Whether [request] is more files for the review on screen rather than an import of its own: not while that review is
     * being written or is the demo library's, and never the demo library, a text that may be one new song for the editor,
     * or anything once the desktop process is on its way out. The preview of one song is joined too, turning into the
     * review of both, see [ImportReportState.bringReviewToFront].
     */
    private fun canJoinReview(request: ImportRequest): Boolean {
        val review = reportState.report as? ImportReport.Review ?: return false
        return activeRequest != null && !review.isDemoLibrary && !request.isDemoLibrary && !request.isNewSongFromText &&
            request.shouldAnnounceResult && !isLeaving()
    }

    /**
     * Reads [request] as a batch of its own and adds what it holds to the review on screen ([withAdded]), leaving every
     * tick and choice made there as it is; what it adds starts ticked, a shared link's own taken names on Replace as in
     * its own review (see [sharedLinkChoices]). The review stays usable meanwhile, but Import waits for the list to be
     * whole. Leaving the screen cancels the reading with the review; a batch that cannot be read is one snackbar, since
     * the review it was to join is still there to answer.
     */
    private suspend fun addToReview(request: ImportRequest) {
        // At once rather than once the batch is read, so that the progress bar answers what the user just did.
        reportState.bringReviewToFront()
        val pending = work.prepare(request, isReviewed = true)
        _isAddingToReview.value = true
        val outcome = try {
            pending.outcome.await()
        } catch (exception: CancellationException) {
            if (work.isPreparationCancelled && currentCoroutineContext().isActive) return
            throw exception
        } catch (exception: Exception) {
            println("Could not read the files to add to the import: ${exception.message}")
            preparationFailureOf(exception)
        } finally {
            // Unlike a batch's own preparation, this one is cancelled with the view model, as the review it joins goes with it.
            work.release(pending)
            _importProgress.value = null
            _isAddingToReview.value = false
        }
        if (work.isPreparationCancelled || reportState.report !is ImportReport.Review) return
        when (outcome) {
            is Preparation.Failed -> messageSink.sendMessage(outcome.message)
            is Preparation.Planned -> {
                request.joinReviewOf(activeRequest)
                val recorded = request.sharedLinkFileId?.let { work.useCases.getSharedLinkImport(it) }
                // Read after the last suspension, so that a tick made while the batch was read is kept.
                val review = reportState.report as? ImportReport.Review ?: return
                val plan = review.plan.withAdded(outcome.plan.withImageFilesSkipped())
                if (plan === review.plan) return
                request.sharedLinkFileId?.let { fileId -> sharedLinkParts += review.plan.sharedLinkPartAdded(fileId = fileId, grown = plan) }
                reportState.report = review.withPlanGrown(plan = plan, recorded = recorded)
            }
        }
    }

    /**
     * The first half of an import only works out what it would do. Nothing is written until the user has reviewed or
     * previewed it and pressed Import, or, for the demo library, until the plan turns out to have no name taken - which
     * is why the plan is kept here rather than on the screen: the answer can arrive long after the screen that started
     * this was left.
     *
     * @return The new song a pasted or shared text came to, which the caller opens the editor on once nothing is in its
     *   way (see [startQueue]); null for anything else, where the import has gone its own way.
     */
    suspend fun import(request: ImportRequest): ImportPlan.SongEntry? {
        // Only ever called by the consumer of the queue, which waits for each import to settle before the next and
        // for any report on screen, and by the first run's demo library before that consumer takes anything, so this
        // holds by construction; it is kept so that a second caller could not start an import over a running one.
        if (request.source.isEmpty || _isImporting.value || activeRequest != null) return null
        val isReviewed = request.isReportedWhileRead(areSongsEnabled = areSongsEnabled())
        if (isReviewed) {
            activeRequest = request
            // Set at once, which is what keeps the queue and What's new waiting, and pushed once nothing is in the way.
            reportState.show(ImportReport.Loading(sourceName = request.source.name, sourceCount = request.source.fileCount))
        }
        return prepare(request = request, isReviewed = isReviewed)
    }

    /**
     * Downloads, reads and compares the batch of [request], and hands the plan on to the preview, the review or the
     * writing, or returns the new song it came to, see [import].
     *
     * @param isReviewed Whether the import screen was put up while the batch was read, see [isReportedWhileRead].
     */
    private suspend fun prepare(request: ImportRequest, isReviewed: Boolean): ImportPlan.SongEntry? {
        _isImporting.update { true }
        return finishPreparation(work.prepare(request = request, isReviewed = isReviewed))
    }

    /**
     * Waits for [pending] and hands what it came to on, see [prepare]. A view model cleared meanwhile leaves the
     * preparation running in [work] with the flags up, and the next one's [startQueue] calls this again on it.
     */
    private suspend fun finishPreparation(pending: ImportWork.Pending): ImportPlan.SongEntry? {
        val request = pending.request
        val isReviewed = pending.isReviewed
        val outcome = try {
            pending.outcome.await()
        } catch (exception: CancellationException) {
            // The user's Cancel cancels only the outcome; the consumer itself still being active is what tells the two apart.
            if (!work.isPreparationCancelled || !currentCoroutineContext().isActive) throw exception
            null
        } catch (exception: Exception) {
            println("Could not read the files to import: ${exception.message}")
            preparationFailureOf(exception)
        }
        work.release(pending)
        // A Cancel can land after the preparation finished but before this resumed (the resumption is dispatched to the
        // main thread, behind a click already queued there): it was still pressed while the screen said "reading", so it wins.
        if (work.isPreparationCancelled || outcome == null) {
            onPreparationCancelled(isReviewed)
            return null
        }
        _importProgress.value = null
        when (outcome) {
            is Preparation.Failed -> {
                _isImporting.value = false
                if (isReviewed) {
                    // The source is kept, so that Try again has something to read.
                    reportState.report = outcome.reportFor(request.source)
                } else {
                    // Nothing has been written, so there is nothing to list either, and one line says all there is to say.
                    if (request.shouldAnnounceResult) messageSink.sendMessage(outcome.message)
                }
            }

            is Preparation.Planned -> {
                // The plan carries everything the rest of the import needs, while the request lives for as long as the
                // review does, which would otherwise keep up to the whole selection's bytes reachable for nothing. The
                // switch is read again here, since one turned off while the batch was read must not open an editor or a
                // preview on a screen the switch removes, and sends the batch to the review.
                val areSongsEnabled = areSongsEnabled()
                val sharedLink = (request.source as? ImportSource.SharedLink)?.link
                // A page's address is read with the import screen up (isReportedWhileRead), which its editor replaces.
                val isPage = request.source is ImportSource.Page
                request.forgetSource()
                val plan = outcome.plan.withImageFilesSkipped()
                val newSong = if ((!isReviewed || isPage) && areSongsEnabled) (if (request.isNewSongFromText) plan.newSongOf() else outcome.plan.pictureSong()) else null
                val isPreviewed = request.shouldReview && areSongsEnabled && !request.isNewSongFromText && plan.isPreviewed
                if (newSong != null) {
                    // The editor is not an import, and leaving it unsaved writes nothing: no progress bar runs under it,
                    // and it is the queue that opens it, since it may have to wait for whatever is on screen.
                    _isImporting.value = false
                    if (isReviewed) reportState.closeLoading()
                    activeRequest = null
                    return newSong
                } else if (request.shouldReview && areSongsEnabled && (plan.librarySong != null || plan.librarySetlist != null)) {
                    // The library holds the song or the setlist as it is, so there is nothing to review and nothing to write: it is opened.
                    activeRequest = null
                    applyImportPlan(
                        plan = plan,
                        choices = ImportChoices(),
                        request = request,
                        sharedLinkParts = listOfNotNull(request.sharedLinkFileId?.let { SharedLinkPart(it, plan.songs.indices, plan.setlists.indices) }),
                        isReported = isReviewed,
                        opensLibraryItem = true,
                    )
                } else if (request.shouldReview || plan.hasConflicts) {
                    activeRequest = request
                    // Nothing is happening while the review is on screen, and a progress bar under it would say otherwise.
                    // The report, which is set first, is what keeps the next batch waiting from here on.
                    // Opening a shared link again is how its recipient updates, and Keep both would leave a numbered copy of
                    // every changed song each time. Only the names this link wrote here before are its to update, though: any
                    // other taken name may be the recipient's own song, which a stranger's link must not start on Replace.
                    val choices = request.sharedLinkFileId?.let { sharedLinkChoices(plan, work.useCases.getSharedLinkImport(it)) } ?: ImportChoices()
                    val review = ImportReport.Review(
                        plan = plan,
                        choices = choices,
                        isDemoLibrary = request.isDemoLibrary,
                        sharedLinkTitle = sharedLink?.title,
                        sharedLinkUpdateCount = choices.conflictResolutions.size,
                        isSongPreview = isPreviewed,
                    )
                    sharedLinkParts = listOfNotNull(request.sharedLinkFileId?.let { SharedLinkPart(it, plan.songs.indices, plan.setlists.indices) })
                    if (isReviewed) reportState.showInPlace(review) else reportState.show(review)
                    _isImporting.value = false
                } else {
                    // Only the demo library, which is asked for by name and known, is written without being asked about.
                    applyImportPlan(
                        plan = plan,
                        choices = ImportChoices(),
                        request = request,
                        sharedLinkParts = listOfNotNull(request.sharedLinkFileId?.let { SharedLinkPart(it, plan.songs.indices, plan.setlists.indices) }),
                        isReported = false,
                    )
                }
            }
        }
        return null
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

    /** The loading stage's Cancel, see [ImportWork.cancel]: nothing is announced, and the queue goes on to its next batch. */
    fun cancelImportPreparation() = work.cancel()

    /**
     * The failure stage's Try again: the same address downloaded again, or the same bytes read again, and the batches that
     * had joined the review ([ImportRequest.joined]) sent back into the queue once it is up again, which adds them to it.
     */
    fun retryImport() {
        val request = activeRequest ?: return
        if ((reportState.report as? ImportReport.Failed)?.canRetry != true) return
        reportState.report = ImportReport.Loading(sourceName = request.source.name, sourceCount = request.source.fileCount)
        _isImporting.update { true }
        scope.launch {
            prepare(request = request, isReviewed = true)
            if (activeRequest === request && reportState.report is ImportReport.Review) request.takeJoined().forEach { work.queue.trySend(it) }
        }
    }

    /**
     * A preview's floating button, see [importPreviewActionsOf]: the preview of one song sets that song's choice to the
     * button's before it imports, and one over the review imports what the review chose, as the review's own Import does.
     */
    fun importFromPreview(action: ImportPreviewAction) {
        if (reportState.report !is ImportReport.Review) return
        when (action) {
            ImportPreviewAction.IMPORT_AS_COPY -> reportState.setImportConflictResolution(PREVIEWED_SONG_KEY, ImportConflictResolution.KEEP_BOTH)
            ImportPreviewAction.REPLACE -> reportState.setImportConflictResolution(PREVIEWED_SONG_KEY, ImportConflictResolution.REPLACE)
            ImportPreviewAction.IMPORT, ImportPreviewAction.IMPORT_REVIEW -> Unit
        }
        confirmImport()
    }

    /**
     * The review's Import. Writing over a library file is the one thing an import cannot take back, so where the choices
     * replace any, [DialogType.ConfirmImportReplace] asks first, and its confirmation is what calls [resolveImport].
     */
    fun confirmImport() {
        val review = reportState.report as? ImportReport.Review ?: return
        // The batch being added is planned against this review, which it could no longer join once written.
        if (_isAddingToReview.value) return
        val count = replaceCount(review.plan, review.choices)
        if (count > 0) dialogHost.showDialog(DialogType.ConfirmImportReplace(count)) else resolveImport()
    }

    /**
     * Writes what the review chose, which is the only thing that ever overwrites a library file. The import screen
     * stays where it is and shows the import being written, and then what it came to; a preview over it is taken off it
     * for that, while the preview of a batch of one song shows the song being written itself.
     */
    fun resolveImport() {
        val review = reportState.report as? ImportReport.Review ?: return
        val request = activeRequest ?: return
        if (_isAddingToReview.value) return
        val parts = sharedLinkParts
        activeRequest = null
        sharedLinkParts = emptyList()
        // Claimed before the review goes away rather than once the import has started, so that nothing waiting for
        // the two of them to be over (see importDemoLibrary) sees a moment with neither.
        _isImporting.update { true }
        reportState.report = ImportReport.Importing
        reportState.closeReviewPreview()
        scope.launch {
            applyImportPlan(
                plan = review.plan,
                choices = review.choices,
                request = request,
                sharedLinkParts = parts,
                isReported = true,
                preview = review.takeIf { it.isSongPreview },
            )
        }
    }

    /**
     * Expects [isImporting] to have been claimed by the caller, which both of them do before anything can observe the gap.
     *
     * @param sharedLinkParts The shared links among the batches of [plan], each remembered with what it wrote.
     * @param isReported Whether the import screen was put up for this import before it started writing, which is
     *   where its outcome goes for as long as that screen has not been left, unless it went clean: the user has just
     *   read the list, and reading it again as a result is one tap more than it is worth. Otherwise the outcome is a
     *   snackbar, unless something was left out or went wrong, which is what the import screen is put up for.
     * @param opensLibraryItem Whether the one song or setlist of the plan is opened once the import is over whatever the
     *   request asked, which is what a batch of one song or setlist the library already holds is for, converted or not.
     * @param preview The preview of a batch of one song the import was started from, which becomes the screen of the song
     *   once it is written, and is put back for the user to try again where nothing could be.
     */
    private suspend fun applyImportPlan(
        plan: ImportPlan,
        choices: ImportChoices,
        request: ImportRequest,
        sharedLinkParts: List<SharedLinkPart>,
        isReported: Boolean,
        opensLibraryItem: Boolean = false,
        preview: ImportReport.Review? = null,
    ) {
        try {
            val result = work.write(
                plan = plan,
                choices = choices,
                bookkeeping = (if (request.isDemoLibrary) DemoLibrary.bookkeeping else ImportBookkeeping()).copy(sharedLinkParts = sharedLinkParts),
            ) { if (request.shouldAnnounceResult) _importProgress.value = it }
            _importProgress.value = null
            val songToOpen = songToOpen(
                plan = plan,
                result = result,
                shouldOpenSong = (request.shouldOpenSong || request.isNewSongFromText || opensLibraryItem) && areSongsEnabled(),
                isConvertedSongOpened = request.isNewSongFromText || opensLibraryItem,
            )
            val itemToOpen = plan.librarySetlist?.takeIf { opensLibraryItem && !result.isFailed && areSongsEnabled() }
                ?.let { LibraryItemToOpen.Setlist(it.fileName) } ?: songToOpen?.let(LibraryItemToOpen::Song)
            val isReportShown = isReported && reportState.isOnBackStack
            val previewedSong = result.importedSongFileNames.singleOrNull()?.takeIf { !result.isFailed }
            when {
                preview != null && isReportShown && previewedSong != null -> showImportedPreview(previewedSong, result, request)
                preview != null && isReportShown -> restorePreview(preview, request, sharedLinkParts)
                // Left while it was being written, which is a choice to hear about it the short way rather than to be
                // taken to the song after all.
                preview != null -> {
                    reportState.report = null
                    messageSink.sendMessage(Message.ImportFinished(result, hasDetails = true))
                }

                // The one song or setlist the system handed over is what the user was after, and the review has been
                // answered: the import screen gives way to it rather than reporting on one file.
                itemToOpen != null -> {
                    // Let go of first, since a report that still says it is being written outlives its screen.
                    if (isReported) reportState.report = null
                    if (isReportShown) reportState.close()
                    scope.launch {
                        // The details screen closes itself over a song the lookup does not hold once the library has been
                        // read, and the lookup is indexed again off the main thread after this write, so a screen pushed now
                        // could find the library read and its song not in it yet. One pushed before the library is read
                        // waits for it by itself. Should the song never arrive (synced away meanwhile), it is not opened
                        // at all rather than out of nowhere much later.
                        if (itemToOpen is LibraryItemToOpen.Song) withTimeoutOrNull(SONG_LOOKUP_TIMEOUT) {
                            songLookup.first { !it.isLibraryRead || itemToOpen.fileName in it.songsByFileName }
                        } ?: return@launch
                        // A first run opens the onboarding over a file the app was opened with; the song follows it, as an
                        // import screen would, rather than going on top of its pages. Finishing the onboarding puts it in
                        // the onboarding's place itself (see `ImportReportState.screenAfterOnboarding`), which leaves
                        // nothing to open here; any other way out leaves it to this.
                        reportState.songAfterOnboarding = songToOpen
                        try {
                            snapshotFlow { backStack.none { it is CampfireDestination.Onboarding } }.first { it }
                        } finally {
                            reportState.songAfterOnboarding = null
                        }
                        openLibraryItem(itemToOpen)
                    }
                    if (request.shouldAnnounceResult) {
                        // A lone song or setlist opened from the library wrote nothing, which one sentence says better than counts.
                        messageSink.sendMessage(
                            if (opensLibraryItem) Message.AlreadyInLibrary else Message.ImportFinished(result, hasDetails = false),
                        )
                    }
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
            val readAgain = if (isReported && reportState.isOnBackStack) request.readAgainAfter(exception) else null
            if (readAgain != null) {
                activeRequest = readAgain
                reportState.report = ImportReport.Failed(ImportFailure.UNREADABLE, readAgain.source.name, readAgain.source.fileCount)
            } else if (preview != null && reportState.isOnBackStack) {
                restorePreview(preview, request, sharedLinkParts)
            } else if (isReported && reportState.isOnBackStack) {
                reportState.report = ImportReport.Finished(null)
            } else {
                if (isReported) reportState.report = null
                if (request.shouldAnnounceResult) messageSink.sendFailure(exception, Message.ImportFailed)
            }
        } finally {
            // A write this view model stopped waiting for lets go of both itself once it ends, see ImportWork.write.
            if (!work.isWriting) {
                _importProgress.value = null
                _isImporting.update { false }
            }
        }
    }

    /**
     * The preview the song [fileName] was imported from becomes that song's screen, in place, once the library holds the
     * song and its text has been read, so that the lyrics on screen stay where they are rather than giving way to a loading
     * indicator, or the screen closing itself over a song the library does not hold yet. Until then the report still says
     * the song is being written, which the preview's button shows. Left meanwhile, the import is said the short way.
     */
    private suspend fun showImportedPreview(fileName: String, result: ImportResult, request: ImportRequest) {
        withTimeoutOrNull(SONG_LOOKUP_TIMEOUT) {
            loadSongText(fileName)
            songLookup.first { fileName in it.songsByFileName }
        }
        reportState.report = null
        val isFollowed = reportState.isOnBackStack && reportState.followImportedSong(fileName)
        if (!request.shouldAnnounceResult) return
        messageSink.sendMessage(if (isFollowed) Message.PreviewedSongImported else Message.ImportFinished(result, hasDetails = true))
    }

    /**
     * Puts the preview of one song back as it was where its import wrote nothing, so that its button can be pressed again:
     * the failure is a snackbar, since the preview is still the screen for the song.
     */
    private fun restorePreview(preview: ImportReport.Review, request: ImportRequest, parts: List<SharedLinkPart>) {
        activeRequest = request
        sharedLinkParts = parts
        reportState.report = preview
        if (request.shouldAnnounceResult) messageSink.sendMessage(Message.ImportFailed)
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
                sharedLinkParts = emptyList()
                reportState.report = null
                // A batch being read to join the review is dropped with it.
                cancelImportPreparation()
            }

            ImportReport.Importing -> Unit
            is ImportReport.Finished, null -> reportState.report = null
        }
    }

    /**
     * Only ever offered from the settings screen, whose back stack holds no song screen and no editor, so there is
     * nothing open on a file that is about to go.
     */
    fun deleteLibrary() = messageSink.launchLibraryChange("delete the library") {
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

    /**
     * Takes the batches of [ImportWork.queue] one at a time, once [demoLibraryDecision] has completed, see
     * `FirstRunController.demoLibraryDecision`. A preparation the last view model left in [work], running or finished
     * with nobody to show it to, is taken first: its import screen is put up again where it had one, and what it came to
     * opens its review or preview here. Ahead of the decision, since a first run's own planting may be that preparation.
     */
    fun startQueue(demoLibraryDecision: Deferred<Unit>) = scope.launch {
        work.preparation?.let { pending ->
            takeBatch(pending.request) { batches ->
                if (pending.isReviewed && !work.isPreparationCancelled) {
                    activeRequest = pending.request
                    reportState.show(ImportReport.Loading(sourceName = pending.request.source.name, sourceCount = pending.request.source.fileCount))
                }
                finishPreparation(pending)?.let { song -> showNewSongEditor(song, pending.request) }
                awaitImportSettled(joined = batches, held = work.held)
            }
        }
        demoLibraryDecision.await()
        while (true) {
            val request = work.held.removeFirstOrNull() ?: work.queue.receiveCatching().getOrNull() ?: break
            takeBatch(request) { batches ->
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
                // start once the exit has begun. Skipping it still settles it.
                if (isLeaving()) return@takeBatch
                import(request)?.let { song -> showNewSongEditor(song, request) }
                awaitImportSettled(joined = batches, held = work.held)
            }
        }
    }

    /**
     * Runs [block] on [request] and the batches that join its review, and settles them all however it ends: here rather
     * than in import, so that the count is honest whatever that did: returned after the preparation was cancelled,
     * failed, or left a review that was answered or abandoned. A batch added to the review is settled with it, since it
     * is written, or dropped, with the rest. One whose preparation outlives this view model is not: the next one takes it.
     */
    private suspend fun takeBatch(request: ImportRequest, block: suspend (batches: MutableList<ImportRequest>) -> Unit) {
        val batches = mutableListOf(request)
        try {
            block(batches)
        } finally {
            batches.forEach { batch -> if (work.preparation?.request !== batch) work.settle(batch) }
        }
    }

    /**
     * Opens the editor on the new song of a pasted or shared text, or of pictures that arrived alone, once nothing is in its
     * way, as the import screen waits (see [ImportReportState.awaitNothingInTheWay]: an editor it replaces has nothing
     * unsaved), says how a text was read, and, unless every picture was refused, suspends until no editor is left on the back stack: the song saved and left, or discarded. It holds the
     * queue as an unanswered review does, so that texts arriving together are opened one after another rather than each
     * replacing the editor of the one before; and since the queue's count stays up meanwhile, What's new waits for it
     * too. The request is settled as soon as the editor is open, since the editor's draft holds the text from then on,
     * and the iOS share extension's inbox can let go of it.
     */
    private suspend fun showNewSongEditor(song: ImportPlan.SongEntry, request: ImportRequest) {
        reportState.awaitNothingInTheWay()
        // A result reopened from the snackbar's Details is a report the queue did not start; it is waited out too.
        awaitImportSettled()
        if (isLeaving()) return
        val isOpened = openNewSongEditor(song, request.suggestion)
        request.settled.complete(Unit)
        if (!isOpened) return
        song.newSongMessage()?.let(messageSink::sendMessage)
        snapshotFlow { backStack.none { it is CampfireDestination.SongEditor } }.first { it }
    }

    /** Notes whether the import screen is still on the back stack, and lets go of what it asked once it is not. */
    fun onBackStackChanged() {
        if (reportState.onBackStackChanged()) onImportReportLeft()
    }
}
