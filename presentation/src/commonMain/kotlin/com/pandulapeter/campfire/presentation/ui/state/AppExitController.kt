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

import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.domain.api.useCases.GetSyncStateUseCase
import com.pandulapeter.campfire.domain.api.useCases.StartScheduledSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.useCases.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverrides
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.EditorSession
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Leaving the app: closing it, with the unsaved changes and the confirmation questions an exit can be parked behind,
 * the app going out of sight, and what a process or a view model about to go still owes the library.
 *
 * @param waitingPreferences The preferences still waiting for their debounce, written together on the way out.
 */
internal class AppExitController(
    private val scope: CoroutineScope,
    private val metronome: Metronome,
    private val dialogHost: DialogHost,
    private val backStack: List<CampfireDestination>,
    private val editorSession: EditorSession,
    private val importController: ImportController,
    private val syncController: SyncController,
    private val overrides: PlayingOverrides,
    private val waitingPreferences: List<DebouncedPreference<*>>,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,
    private val getSyncState: GetSyncStateUseCase,
    private val startScheduledSynchronization: StartScheduledSynchronizationUseCase,
) {

    /**
     * Set once the desktop process is on its way out ([settleSynchronizationBeforeExit]) and never cleared, so that the
     * import queue's consumer starts no batch the exit would cut off halfway. Touched only on the main thread, like
     * `ImportController.preparation`.
     */
    var isLeaving = false
        private set

    /**
     * The exit that asked the `UnsavedChanges` question, run once it is answered with Save or Discard. Any other way
     * the dialog goes away is staying, and that is reported too: on macOS the exit may be the system's own quit
     * request, which has to be answered either way (see the desktop app module).
     */
    private var pendingExit: PendingExit? = null

    /**
     * Taken rather than read, so that exactly one of an exit's two callbacks can ever run: the branch that is about to
     * run the exit holds it before `EditorSession.leaveEditor` dismisses the dialog, which would otherwise report it as cancelled.
     */
    fun takePendingExit() = pendingExit.also { pendingExit = null }

    /**
     * Closing the application, which is a way out of the editor like any other. A save that is still being written
     * is waited for first, since the process ends with [onExit] - and since only then is it known whether it worked:
     * with unsaved text in the editor, which is also what a save that failed leaves behind, the `UnsavedChanges`
     * question is asked, and [onExit] only runs once that has been answered with something other than staying.
     *
     * @param onCancelled Called instead of [onExit] when the user answers the `UnsavedChanges` question by staying -
     *   dismissing it, or a save that failed. The macOS quit handler needs it: a quit request that is not answered
     *   one way or the other leaves the system waiting.
     */
    fun requestExit(onExit: () -> Unit, onCancelled: () -> Unit = {}) {
        scope.launch {
            editorSession.currentSaveJob?.join()
            // A second request - Cmd+Q pressed again while the question is up - answers the first one, which is
            // still waiting for something.
            takePendingExit()?.onCancelled?.invoke()
            if (editorSession.hasUnsavedEditorText() && backStack.lastOrNull() is CampfireDestination.SongEditor) {
                pendingExit = PendingExit(exit = onExit, onCancelled = onCancelled)
                dialogHost.showDialog(DialogType.UnsavedChanges)
            } else {
                onExit()
            }
        }
    }

    /**
     * Closing the application with the key that is otherwise only ever a step back - the desktop's Escape, pressed
     * once more than there were screens to leave. That is a habit of the hand rather than a decision, and on a music
     * stand it ends a song and a click mid-performance, so it asks first; the window's close button and the system's
     * quit are deliberate and go straight to [requestExit]. A second press while the question is up dismisses it.
     */
    fun confirmExit(onExit: () -> Unit) {
        confirmedExit = onExit
        dialogHost.showDialog(DialogType.ConfirmExit)
    }

    /** The answer to [DialogType.ConfirmExit] that leaves. */
    fun exitConfirmed() {
        val exit = confirmedExit ?: return
        confirmedExit = null
        dialogHost.dismissDialog()
        requestExit(exit)
    }

    /** The exit [DialogType.ConfirmExit] is asking about, kept only while that question is the one on screen. */
    private var confirmedExit: (() -> Unit)? = null

    /**
     * Lets the sync runs the library still owes the cloud folder happen before a desktop process ends, which is where
     * a quit leads once [requestExit] has let it through - the shell hides the window first, so the quit looks as
     * immediate as it is. An import that is being written is let finish first, for up to [EXIT_IMPORT_GRACE], since
     * its songs are written before its setlists and the progress dialog asked for Campfire to be kept open; one that is
     * still being read is cancelled, having written nothing, and no further batch is started. Waited for before the
     * sync run, so that the run carries the whole import. The automatic run that is waiting for the library to settle
     * is started next: dropped, the change made just before quitting would reach the other devices only the next time
     * this computer opens Campfire.
     * A run that is going is waited for, and so is one chained behind it, the metronome having been stopped first. Past
     * [EXIT_SYNC_GRACE] the run is stopped instead and its winding down waited for, briefly, since a stopped run writes its index and clears the marker
     * that would otherwise have the next launch report it as interrupted and start no run of its own. Before any of
     * that, the stored editor draft is brought in line with the editor, written or removed, and what is still waiting
     * for its debounce (the text size, the metronome settings, the export options and the tempo and capo overrides)
     * is written, since the process ends right after.
     */
    suspend fun settleSynchronizationBeforeExit() {
        isLeaving = true
        importController.cancelImportPreparation()
        // A conflicts question that is up has written nothing and has already let the import flag go, so it is dropped
        // here rather than waited for.
        withTimeoutOrNull(EXIT_IMPORT_GRACE) { importController.isImporting.first { !it } }
        // The stored draft is removed by a collector a few hops after the editor lets its text go, which a process that
        // ends now would not wait for: a Discard answered on the way out would come back as "unsaved changes restored".
        if (!editorSession.isEditorDraftRecoveryPending.value) editorSession.storeEditorDraft(editorSession.currentEditorDraftToStore())
        // The window is already hidden, so a click still sounding while the run is waited for would come from nowhere.
        metronome.stop()
        // The process ends right after this, and onCleared's detached write would race it - or, on a macOS Quit, never
        // run. Before the sync wait, so that an override written now is in the library a run that is still to start carries.
        writeWaitingPreferences()
        overrides.takeWaitingOverrideWrites()()
        val isSyncing = { state: SyncState -> state is SyncState.Connected && state.isSyncing }
        val hasSettled = withTimeoutOrNull(EXIT_SYNC_GRACE) {
            // The state is the repository's own rather than syncState, which only follows it a hop to the main thread
            // later and would still say nothing is going for a run that has just been started.
            while (startScheduledSynchronization() != null) {
                getSyncState().first { !isSyncing(it) }
            }
        } != null
        if (!hasSettled) {
            syncController.cancelSynchronization()
            withTimeoutOrNull(EXIT_SYNC_STOP_GRACE) { getSyncState().first { !isSyncing(it) } }
        }
    }

    /**
     * Called by the app whenever it stops being the one in front (ON_PAUSE), which is the last moment it is certainly
     * running: iOS ends a process in the background without a word, and so does Android to a task swiped away and a
     * mobile browser to a tab it wants the memory of. Stores the unsaved text, or removes what was stored when there
     * is none. Nothing before the draft a previous run left has been read, or it would be written over.
     *
     * An automatic sync run that is still waiting for the library to settle starts now as well: the phones keep a run
     * alive in the background only once the app has told them about it, which it has to do before this callback is
     * over - the composition may not get another frame. So the progress of the run that is going is returned for the
     * caller to hand over there and then, rather than left to arrive through [syncState], which it would only do
     * after a hop to the main thread this callback is holding.
     */
    fun onAppPaused(): SyncProgress? {
        val syncProgress = startScheduledSynchronization()
        if (!editorSession.isEditorDraftRecoveryPending.value) {
            val draft = editorSession.currentEditorDraftToStore()
            scope.launch { editorSession.storeEditorDraft(draft) }
        }
        return syncProgress
    }

    /**
     * The view model going (the activity finished, or the desktop window disposed) does not take what is still waiting
     * for its debounce with it - the text size, the metronome settings, the export options, a tempo or a capo: those
     * are written on a scope of their own, since this one is being cancelled.
     *
     * It does take the click, though. This is the app being left rather than being sent to the background - the one is
     * a finished Activity, the other a paused one - and the click is kept alive across the background for a phone on a
     * music stand with its screen off, not for an app the user has closed; the metronome is a singleton that would
     * otherwise go on clicking, with its notification, under a process nobody is looking at any more.
     */
    fun onCleared() {
        metronome.stop()
        val writeWaitingOverrides = overrides.takeWaitingOverrideWrites()
        // Written on a scope of their own, since this one is being cancelled: each was waiting for its debounce, which
        // this scope's cancellation would otherwise drop.
        CoroutineScope(Dispatchers.Default + NonCancellable).launch {
            try {
                writeWaitingPreferences()
                writeWaitingOverrides()
            } catch (exception: Exception) {
                // A bare scope has no handler, and an exception escaping it would end the process.
                println("Could not write the waiting values: ${exception.message}")
            }
        }
    }

    /**
     * Writes the text size, the metronome settings and the export options that are still waiting for their debounce, in
     * one read-modify-write of the preferences, for a view model or a process that is about to go. Each is let go of
     * only if nothing newer arrived meanwhile, as the collectors do.
     */
    private suspend fun writeWaitingPreferences() = DebouncedPreference.flushAll(
        *waitingPreferences.toTypedArray(),
        write = updateUserPreferences::invoke,
    )

    /** See [PlayingOverrides.takeWaitingOverrideWrites]. */
    private fun takeWaitingOverrideWrites() = overrides.takeWaitingOverrideWrites()

    /** Lets go of the exits a dialog change leaves behind, as a listener of [DialogHost.setVisibleDialog]. */
    fun onBeforeDialogChange(dialogType: DialogType?) {
        // An exit the question was asked for and that is not being run is an exit that was cancelled: its caller
        // may be waiting to hear so (the macOS quit request is).
        if (dialogType != DialogType.UnsavedChanges) takePendingExit()?.onCancelled?.invoke()
        if (dialogType != DialogType.ConfirmExit) confirmedExit = null
    }

    private companion object {
        /**
         * Long enough for the run an edit asks for, short enough to never look hung. The desktop's `SingleInstance.kt`
         * waits `CLOSING_INSTANCE_WAIT_MILLIS` for a closing process, which has to stay above this, [EXIT_SYNC_STOP_GRACE]
         * and [EXIT_IMPORT_GRACE] together, so raising any of them means raising that too.
         */
        val EXIT_SYNC_GRACE = 15.seconds
        val EXIT_SYNC_STOP_GRACE = 2.seconds

        /** Long enough for a few hundred songs to be written; only a stalled disk reaches it. */
        val EXIT_IMPORT_GRACE = 30.seconds
    }
}
