/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.useCases.GetEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveEditorDraftUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.state.PendingExit
import com.pandulapeter.campfire.presentation.ui.state.SongTextStore
import com.pandulapeter.campfire.presentation.ui.state.asState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The editor: the text it holds and has not written yet, the draft kept on disk in case the process ends in the
 * background, the field kept across a recreation, its ways out and its saves.
 *
 * @param updateBackStack Changes the back stack, see `CampfireViewModel.updateBackStack`.
 * @param popBackStack Leaves the screen on top, see `CampfireViewModel.popBackStack`.
 * @param takePendingExit Takes the exit the unsaved changes question was asked for, see `CampfireViewModel.takePendingExit`.
 * @param requestExit Closes the application, see `CampfireViewModel.requestExit`.
 */
internal class EditorSession(
    private val scope: CoroutineScope,
    private val dialogHost: DialogHost,
    private val messageSink: MessageSink,
    private val songTextStore: SongTextStore,
    private val songRenderer: SongRenderer,
    private val backStack: List<CampfireDestination>,
    private val userPreferences: StateFlow<UserPreferences?>,
    private val arePreferencesLoaded: StateFlow<Boolean>,
    private val getSongContent: GetSongContentUseCase,
    private val getEditorDraft: GetEditorDraftUseCase,
    private val saveEditorDraft: SaveEditorDraftUseCase,
    private val updateBackStack: (SnapshotStateList<CampfireDestination>.() -> Unit) -> Unit,
    private val popBackStack: () -> Unit,
    private val takePendingExit: () -> PendingExit?,
    private val requestExit: (onExit: () -> Unit, onCancelled: () -> Unit) -> Unit,
) {

    /**
     * True until the draft a previous run left behind has been read and, where there was one, the editor reopened on
     * it (see [recoverEditorDraft]). The launch screen waits for it, so that the app is uncovered on the editor rather
     * than on the songs a moment before the editor slides in over them.
     */
    private val _isEditorDraftRecoveryPending = MutableStateFlow(true)
    val isEditorDraftRecoveryPending: StateFlow<Boolean> = _isEditorDraftRecoveryPending.asStateFlow()

    /** Completed with whether the editor was reopened on a stored draft, which the other start-up navigations defer to. */
    val editorDraftRecovery = CompletableDeferred<Boolean>()

    /**
     * What the open editor currently has in it, reported by the screen as it is typed but never written until the
     * user asks for it. The text itself still lives in the field's own state; this copy exists so that leaving the
     * screen can be stopped ([navigateBack]) and the save finished from the confirmation dialog without the screen
     * that holds the field being there any more. Null whenever no editor is open.
     */
    private val _editorDraft = MutableStateFlow<SongContent?>(null)
    val editorDraft: StateFlow<SongContent?> = _editorDraft.asStateFlow()

    /**
     * The draft as it was last put on disk, so that a pause that finds the same text writes nothing. Only read or written
     * under [editorDraftStoreMutex], and only once [recoverEditorDraft] has read what a previous run left.
     */
    private var storedEditorDraft: SongContent? = null

    private val editorDraftStoreMutex = Mutex()

    /**
     * The open editor's field as its screen last saved it, by file name. The screen's saved state only holds the text,
     * and not even that for a long document (see the editor's saver); this holds the field itself, for the
     * restorations this object lives through - a rotation, the system changing its theme or language - where the
     * undo history and a draft of any length can simply be handed back.
     */
    private var retainedEditorField: Pair<String, TextFieldState>? = null

    /** Asked for by the confirmation dialog and answered by the editor screen, see [revertEditorChanges]. */
    private val _editorRevertRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val editorRevertRequests = _editorRevertRequests.asSharedFlow()

    /**
     * The metadata dialogs' changes to the editor's text, opened from its own overflow menu. Answered by the screen,
     * like [editorRevertRequests], since the field is the screen's and a change made through its own editing is one
     * more step of its undo history that Save writes with everything else typed.
     */
    private val _editorTextEdits = MutableSharedFlow<EditorTextEdit>(extraBufferCapacity = 1)
    val editorTextEdits = _editorTextEdits.asSharedFlow()

    /** True while the editor's text differs from what is on disk, which is what [navigateBack] asks before it leaves. */
    val hasUnsavedEditorChanges = combine(_editorDraft, songTextStore.songTexts, userPreferences) { draft, songTexts, _ ->
        draft != null && draft.text != songTexts[draft.fileName]?.let(::editorTextOf)
    }.asState(scope, false)

    /**
     * The last write of the editor's text, from its Save action or from the `UnsavedChanges` dialog, which closing the
     * application waits for, see [requestExit].
     */
    var currentSaveJob: Job? = null
        private set

    /** The save the `UnsavedChanges` dialog is waiting for, so that a second press of its Save does not start another. */
    private var editorLeaveJob: Job? = null

    /** True while the editor's text is being written, which it shows in place of its "Saved" label. */
    private val _isSavingSong = MutableStateFlow(false)
    val isSavingSong: StateFlow<Boolean> = _isSavingSong.asStateFlow()

    /**
     * The notation the editor's field is written in, the reader's own: the file is converted out of the standard one
     * as it is opened ([SongRenderer.editorTextOf]) and back into it as it is saved ([SongRenderer.fileTextOf]). It never changes under an open
     * editor, since Settings is only reached by selecting a top level screen, which takes the editor off the stack.
     *
     * Under a numbering it is the standard notation, and only the preview is in numbers: a field in numbers would make
     * correcting a wrong `{key}` move every chord of the song at Save, the numbers staying where they are, and a key
     * half typed would leave every number with nothing to count from (see [UserPreferences.Notation.forTyping]).
     */
    val editorNotation get() = (userPreferences.value?.chordSpelling?.notation ?: UserPreferences.Notation.STANDARD).forTyping

    fun openEditor(fileName: String, shouldStartInsideFirstSection: Boolean = false) {
        if (backStack.lastOrNull() !is CampfireDestination.SongEditor) {
            updateBackStack { add(CampfireDestination.SongEditor(fileName = fileName, shouldStartInsideFirstSection = shouldStartInsideFirstSection)) }
        }
    }

    /** Reported by the editor on every change, see [_editorDraft]. Nothing is written here. */
    fun onEditorTextChanged(fileName: String, text: String) = _editorDraft.update { SongContent(fileName = fileName, text = text) }

    /**
     * Reported by the editor once it is gone, whatever became of the text it had. An editor that is still on the
     * stack is only being composed again - a rotation, the system changing its theme - and its draft stands until the
     * new composition reports it: dropped in between, the moment would read as "nothing unsaved" to everything that
     * asks, the update gate and the way out of the editor included.
     */
    fun onEditorClosed(fileName: String) {
        if (backStack.none { it is CampfireDestination.SongEditor && it.fileName == fileName }) {
            _editorDraft.update { draft -> draft?.takeUnless { it.fileName == fileName } }
        }
    }

    /**
     * Called by the editor whenever its state is saved. That includes one last time as the screen leaves for good,
     * by which time the stack has let go of it - and then there is nothing to keep the field for.
     */
    fun retainEditorField(fileName: String, textFieldState: TextFieldState) {
        if (backStack.any { it is CampfireDestination.SongEditor && it.fileName == fileName }) {
            retainedEditorField = fileName to textFieldState
        }
    }

    fun retainedEditorField(fileName: String) = retainedEditorField?.takeIf { it.first == fileName }?.second

    /** Reported by the editor when it came back from a saved state that could not hold its unsaved text. */
    fun onEditorDraftLost() {
        messageSink.sendMessage(Message.EditorDraftLost)
    }

    /**
     * The unsaved text as the file would hold it, or null when nothing is unsaved. Stored as the file would hold it
     * rather than as the field shows it, so that it means the same chords whatever the notation is by the time it is
     * reopened.
     */
    fun currentEditorDraftToStore() = _editorDraft.value?.takeIf { hasUnsavedEditorText() }?.let { it.copy(text = fileTextOf(it.text)) }

    /** Not cancellable once started: a pause is often the last thing the process does. A write that fails is only a copy lost. */
    suspend fun storeEditorDraft(draft: SongContent?) = withContext(NonCancellable) {
        editorDraftStoreMutex.withLock {
            if (draft == storedEditorDraft) return@withLock
            try {
                saveEditorDraft(draft)
                storedEditorDraft = draft
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                println("Could not store the editor's draft: ${exception::class.simpleName}")
            }
        }
    }

    /**
     * Reopens the editor on the draft a previous run left, answering whether it did. Not over a stack that already has
     * an editor - an Android process restored on the editor has its text from the saved state - and not for a draft the
     * file already holds. A file that is gone is reopened all the same: the draft is all there is, and saving puts the
     * file back, which is what an editor whose file goes while it is open does too.
     */
    private suspend fun recoverEditorDraft(): Boolean {
        val draft = try {
            getEditorDraft()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not read the editor's draft: ${exception::class.simpleName}")
            null
        }
        editorDraftStoreMutex.withLock { storedEditorDraft = draft }
        if (draft == null || backStack.any { it is CampfireDestination.SongEditor }) return false
        val content = getSongContent(draft.fileName)
        // The draft is in the file's notation and the field shows the reader's, which only the preferences can say.
        arePreferencesLoaded.first { it }
        val text = editorTextOf(draft.text)
        if (content != null && editorTextOf(content.text) == text) {
            storeEditorDraft(null)
            return false
        }
        content?.let { songTextStore.updateSongTexts { texts -> texts + (it.fileName to it.text) } }
        // The draft is the editor's before the editor exists, so that nothing asking whether there is unsaved text in
        // the moments before it composes - a pause, the update gate - hears "no".
        onEditorTextChanged(fileName = draft.fileName, text = text)
        updateBackStack { add(CampfireDestination.SongEditor(fileName = draft.fileName)) }
        // Taken by the editor as its field, see LoadedSongEditor, the same way a field it retained across a rotation is.
        retainedEditorField = draft.fileName to TextFieldState(initialText = text)
        messageSink.sendMessage(Message.EditorDraftRestored)
        if (content == null) messageSink.sendMessage(Message.EditedSongFileGone)
        return true
    }

    /**
     * The "Save" answer of the unsaved changes dialog. The editor holds the only copy of the text until the file
     * does, so leaving - and, where the dialog was asked by [requestExit], ending the process - is what a write that
     * succeeded earns, not what follows one that was started. The dialog stays up while the file is written. A write
     * that fails takes the dialog away and leaves the editor as it was, which is what the same failure does behind
     * the editor's own Save button; a dialog dismissed in the meantime still means staying, with the text saved.
     */
    fun saveEditorChangesAndLeave() {
        if (editorLeaveJob?.isActive == true) return
        val draft = _editorDraft.value ?: return leaveEditorWithoutSaving()
        editorLeaveJob = scope.launch {
            val isSaved = writeEditorText(fileName = draft.fileName, text = draft.text)
            when {
                !isSaved -> dialogHost.dismissDialog()
                dialogHost.visibleDialog.value == DialogType.UnsavedChanges -> {
                    // Taken before leaving, which dismisses the dialog and would otherwise report this exit as
                    // cancelled.
                    val exit = takePendingExit()
                    leaveEditor()
                    exit?.exit?.invoke()
                }
            }
        }.also { currentSaveJob = it }
    }

    /** The "Discard" answer of the unsaved changes dialog, and the only way typed text is ever thrown away. */
    fun leaveEditorWithoutSaving() {
        val exit = takePendingExit()
        leaveEditor()
        exit?.let { requestExit(it.exit, it.onCancelled) }
    }

    /**
     * The confirmed "Revert" action of the editor. Answered by the screen rather than here, because the text field
     * belongs to it and putting the saved text back has to go through the field's own editing (and its undo
     * history); what the text goes back to is [songTexts], which is the file as it was last read or written.
     */
    fun revertEditorChanges() {
        dialogHost.dismissDialog()
        _editorRevertRequests.tryEmit(Unit)
    }

    private fun leaveEditor() {
        // The draft goes first: with it still there, popping the editor would only ask the same question again.
        _editorDraft.update { null }
        dialogHost.dismissDialog()
        popBackStack()
    }

    /** The editor's Save action. Fire and forget: the outcome reaches the user as the editor's own state. */
    fun saveSongContent(fileName: String, text: String) = scope.launch {
        writeEditorText(fileName = fileName, text = text)
    }.also { currentSaveJob = it }

    /**
     * Writes the edited text, keeps the copy the viewer renders from in step, and answers whether the file now holds
     * it. A failure is reported from here as [Message.SaveFailed], so that every way of saving says the same thing.
     * Runs on [NonCancellable] because the last save of an editing session can be started as the screen is going
     * away, which cancels its scope.
     */
    private suspend fun writeEditorText(fileName: String, text: String) = try {
        _isSavingSong.update { true }
        val fileText = fileTextOf(text)
        withContext(NonCancellable) {
            songTextStore.songWriteMutex.withLock { songTextStore.writeSongContent(fileName = fileName, text = fileText) }
        }.also { isWritten ->
            // A chord typed in a spelling the notation has a second one for (a German Bb, a ♭) is written the one way
            // the file holds it, so the field is brought to what was written, or it would never stop being unsaved.
            val writtenText = editorTextOf(fileText)
            if (isWritten && writtenText != text) {
                _editorTextEdits.tryEmit(EditorTextEdit(fileName = fileName) { current -> if (current == text) writtenText else current })
            }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not save the song \"$fileName\": ${exception.message}")
        messageSink.sendMessage(Message.SaveFailed)
        false
    } finally {
        _isSavingSong.update { false }
    }

    /** [hasUnsavedEditorChanges] as of this moment, for a decision taken right after a write rather than drawn. */
    fun hasUnsavedEditorText() = _editorDraft.value?.let { it.text != songTextStore.songTexts.value[it.fileName]?.let(::editorTextOf) } == true

    /** The text of a file as the editor shows it, see [SongRenderer.editorTextOf]. */
    private fun editorTextOf(fileText: String) = songRenderer.editorTextOf(fileText, editorNotation)

    /** The editor's text as the file is to hold it, in the standard notation. */
    private fun fileTextOf(editorText: String) = songRenderer.fileTextOf(editorText, editorNotation)

    /**
     * The editor's unsaved text as a previous run left it, when the process ended in the background (see
     * onAppPaused). Once that is settled, whatever leaves the editor with nothing unsaved takes the stored
     * draft with it - a save, a discard, a revert, the song deleted - so a crash after a save never brings back
     * text that was saved. Asked of the draft itself rather than of hasUnsavedEditorChanges alone, which may not
     * have caught up yet with a draft this has just reopened.
     */
    fun startRecovery() = scope.launch {
        try {
            editorDraftRecovery.complete(recoverEditorDraft())
        } finally {
            editorDraftRecovery.complete(false)
            _isEditorDraftRecoveryPending.value = false
        }
        hasUnsavedEditorChanges.collect { if (!it && !hasUnsavedEditorText()) storeEditorDraft(null) }
    }

    /** Drops the field kept for an editor once no editor is left on the back stack. */
    fun onBackStackChanged() {
        if (backStack.none { it is CampfireDestination.SongEditor }) retainedEditorField = null
    }

    /** Lets go of the editor's text, for a song that has just been deleted, which nobody is asked to save. */
    fun clearEditorDraft() = _editorDraft.update { null }

    /** Hands [edit] to the open editor, see [editorTextEdits]. */
    fun emitEditorTextEdit(edit: EditorTextEdit) = _editorTextEdits.tryEmit(edit)
}
