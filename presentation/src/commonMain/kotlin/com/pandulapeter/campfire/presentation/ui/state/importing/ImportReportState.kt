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

import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.ImportPreview
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import com.pandulapeter.campfire.presentation.ui.screens.importReport.followingLibraryFileNames
import com.pandulapeter.campfire.presentation.ui.screens.importReport.isUnfinished
import com.pandulapeter.campfire.presentation.ui.screens.importReport.review.withGroupResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the import screen shows and whether it is up: the report from the files being read through the review to the
 * result, its search field, the review's choices, and the screen being put on the back stack once nothing is in its
 * way. The import work itself, which writes the report, is `ImportController`'s.
 *
 * @param updateBackStack Changes the back stack, see `Navigator.updateBackStack`.
 * @param hasUnsavedEditorText Whether the editor holds text that is not written yet, which the import screen waits for.
 */
internal class ImportReportState(
    private val scope: CoroutineScope,
    private val dialogHost: DialogHost,
    private val backStack: List<CampfireDestination>,
    private val updateBackStack: (SnapshotStateList<CampfireDestination>.() -> Unit) -> Unit,
    private val songTexts: StateFlow<Map<String, String>>,
    private val editorDraft: StateFlow<SongContent?>,
    private val hasUnsavedEditorText: () -> Boolean,
) {

    /**
     * Open for good, since its field is part of the import screen's list rather than something the screen opens, and
     * emptied whenever that screen is left. Not restored, since the screen it belongs to never is, see [backStack].
     */
    val search = SearchState(isInitiallyOpen = true)

    /**
     * What [CampfireDestination.ImportReport] shows: every import the user started, from the files being read through
     * the review of what they hold to the import being written and what it came to, and of the rest whatever went
     * anywhere but the one way that needs no more than a snackbar. Null while no import has anything to report, and an
     * import that has one keeps the next batch waiting until the screen is left, see `ImportController.awaitImportSettled`.
     */
    private val _importReport = MutableStateFlow<ImportReport?>(null)
    val importReport: StateFlow<ImportReport?> = _importReport.asStateFlow()

    /** [importReport] as `ImportController` writes it. */
    var report: ImportReport?
        get() = _importReport.value
        set(value) {
            _importReport.value = value
        }

    /**
     * Whether the last change to [backStack] left the import's screen on it, see [onBackStackChanged]: the import screen,
     * or the preview that stands in for it where the batch is one song ([ImportReport.Review.isSongPreview]).
     */
    var isOnBackStack = false
        private set

    /**
     * Counts the reports [show] has put up, so that a waiting one is pushed only if no later one replaced
     * it. The value cannot be compared instead: [followReportedFileNames] rewrites a waiting report as the library moves.
     */
    private var request = 0

    /**
     * Sets [report] at once, which is what keeps the next batch waiting, and puts the import screen on top of whatever
     * is on screen once nothing else is in its way: a dialog, whose input would otherwise be left under the screen, or
     * an editor holding unsaved text, which is only ever asked about while it is on top (see `Navigator.navigateBack`) and
     * would be one closed window away from being lost under it, or the onboarding, which a first run opens even while a
     * file the app was opened with is read behind it, and which the import screen follows as it closes. Launched rather
     * than awaited, so that the import that asked can finish meanwhile.
     */
    fun show(report: ImportReport) {
        val request = ++request
        _importReport.value = report
        scope.launch {
            awaitNothingInTheWay()
            val shown = _importReport.value
            if (this@ImportReportState.request == request && shown != null && !isOnBackStack) {
                updateBackStack { add(screenOf(shown)) }
            }
        }
    }

    /**
     * Puts [review] where the report under way is, the import screen included: the preview of a batch of one song
     * ([ImportReport.Review.isSongPreview]) takes the place of the import screen that showed it being read, rather than
     * going over it, since Back from the preview leaves the import. Where [show] is still waiting for the screen to go up,
     * it puts up the preview instead.
     */
    fun showInPlace(review: ImportReport.Review) {
        _importReport.value = review
        if (!review.isSongPreview) return
        val index = backStack.indexOf(CampfireDestination.ImportReport)
        if (index >= 0) updateBackStack { set(index, screenOf(review)) }
    }

    /** The screen [report] is shown on: the import screen, or the preview of the one song of a batch that is nothing else. */
    private fun screenOf(report: ImportReport) = if (report is ImportReport.Review && report.isSongPreview) {
        CampfireDestination.SongDetails(songFileNames = emptyList(), setlistFileName = null, initialIndex = 0, importPreview = ImportPreview.SingleSong)
    } else {
        CampfireDestination.ImportReport
    }

    /**
     * Opens the songs of the review's rows ([songIndices], the places of their entries in the plan's songs, in the order
     * the rows are listed) in a preview over the import screen, on the one at [index], see [ImportPreview.isReviewPager].
     */
    fun openReviewPreview(songIndices: List<Int>, index: Int) {
        val review = _importReport.value as? ImportReport.Review ?: return
        if (backStack.lastOrNull() != CampfireDestination.ImportReport || songIndices.isEmpty()) return
        val preview = ImportPreview(songIndices = songIndices, planSongCount = review.plan.songs.size, isReviewPager = true)
        updateBackStack {
            add(CampfireDestination.SongDetails(songFileNames = emptyList(), setlistFileName = null, initialIndex = index.coerceIn(songIndices.indices), importPreview = preview))
        }
    }

    /** Takes a preview over the review off the back stack, which the review's Import pressed in it does: the import screen shows the rest. */
    fun closeReviewPreview() {
        if (backStack.any { it.isReviewPreview }) updateBackStack { removeAll { it.isReviewPreview } }
    }

    /**
     * Rewrites the preview of a batch of one song into the screen of [fileName], the song it has been written as, in
     * place: the entry keeps its id, so Navigation 3 takes it for the same screen and nothing slides, and the screen
     * crossfades what changes. Whether it was still there to rewrite.
     */
    fun followImportedSong(fileName: String): Boolean {
        val index = backStack.indexOfFirst { it.isSongPreview }
        if (index < 0) return false
        val preview = backStack[index] as CampfireDestination.SongDetails
        updateBackStack { set(index, preview.copy(songFileNames = listOf(fileName), initialIndex = 0, importPreview = null)) }
        return true
    }

    /**
     * Suspends until nothing [show] waits for is in the way: no dialog, no editor holding unsaved text on the back stack,
     * and no onboarding. Also what the New song form of a pasted or shared text waits for, see `ImportController.startQueue`.
     */
    suspend fun awaitNothingInTheWay() {
        combine(dialogHost.visibleDialog, editorDraft, songTexts, snapshotFlow { backStack.toList() }) { dialog, _, _, stack ->
            dialog == null && !(hasUnsavedEditorText() && stack.any { it is CampfireDestination.SongEditor }) &&
                stack.none { it is CampfireDestination.Onboarding }
        }.first { it }
    }

    /** Takes the import's screen off the back stack, with whatever is on top of it, see `ImportController.onImportReportLeft`. */
    fun close() {
        val index = backStack.indexOfFirst { it == CampfireDestination.ImportReport || it.isSongPreview }
        if (index >= 0) updateBackStack { while (size > index) removeAt(lastIndex) }
    }

    /** A group's box, over the rows the screen shows, which is only the matching ones while a search narrows them. */
    fun setImportEntriesChosen(keys: Collection<ImportEntryKey>, isChosen: Boolean) = updateChoices { choices ->
        choices.copy(leftOut = if (isChosen) choices.leftOut - keys.toSet() else choices.leftOut + keys)
    }

    /** What a name already taken does: one row's choice for [key]. */
    fun setImportConflictResolution(key: ImportEntryKey, resolution: ImportConflictResolution) = updateChoices { choices ->
        choices.copy(conflictResolutions = choices.conflictResolutions + (key to resolution))
    }

    /**
     * The group header's choice for all of them, over the rows the screen shows, which is only the matching ones while a
     * search narrows them, as the group's box acts: see [withGroupResolution].
     */
    fun setImportGroupConflictResolution(
        keys: Collection<ImportEntryKey>,
        isFiltered: Boolean,
        resolution: ImportConflictResolution,
    ) = updateChoices { it.withGroupResolution(keys = keys, isFiltered = isFiltered, resolution = resolution) }

    /** The tags every song the import writes is given, as the review's tag sheet left them. */
    fun setImportTags(tags: List<String>) = updateChoices { it.copy(tags = tags) }

    private fun updateChoices(update: (ImportChoices) -> ImportChoices) = _importReport.update { report ->
        if (report is ImportReport.Review) report.copy(choices = update(report.choices)) else report
    }

    /**
     * Keeps the import screen's result naming the files the library holds, so that coming back to it from a song opened
     * from its list and deleted or renamed there shows the library as it now is rather than a row that no longer opens.
     */
    fun followReportedFileNames(fileName: (String) -> String?) = _importReport.update { report ->
        if (report is ImportReport.Finished && report.result != null) ImportReport.Finished(report.result.followingLibraryFileNames(fileName)) else report
    }

    /**
     * Says that a process ended behind an unwritten import, and keeps [savedStateStore] noting whether one is. The
     * import screen is never restored, and its plan is far too large to be saved, so a process ended behind an
     * unwritten import says so instead. Read before the collector writes the first report over it.
     */
    fun startReportingInterruption(savedStateStore: SavedStateStore, messageSink: MessageSink) {
        val wasImportInterrupted = savedStateStore.restore<Boolean>(SavedStateStore.IMPORT_UNFINISHED_KEY) == true
        scope.launch { importReport.collect { savedStateStore.persist(SavedStateStore.IMPORT_UNFINISHED_KEY, it.isUnfinished) } }
        if (wasImportInterrupted) messageSink.sendMessage(Message.ImportInterrupted)
    }

    /**
     * Notes whether the import screen is still on the back stack, clears its search field once it is not, and answers
     * whether it has just left, for `ImportController` to let go of what the screen asked.
     */
    fun onBackStackChanged(): Boolean {
        val wasOnBackStack = isOnBackStack
        isOnBackStack = backStack.any { it == CampfireDestination.ImportReport || it.isSongPreview }
        val hasLeft = wasOnBackStack && !isOnBackStack
        if (hasLeft) search.textFieldState.clearText()
        return hasLeft
    }
}

/** Whether this is the preview that stands in for the import screen, see [ImportReport.Review.isSongPreview]. */
private val CampfireDestination.isSongPreview get() = this is CampfireDestination.SongDetails && importPreview?.isReviewPager == false

/** Whether this is a preview paging through the review's rows, over the import screen. */
private val CampfireDestination.isReviewPreview get() = this is CampfireDestination.SongDetails && importPreview?.isReviewPager == true
