/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.share.ShareTarget
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import com.pandulapeter.campfire.presentation.ui.state.SongLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Saves the form sheet that is up, see [SavedDialog], and reopens the one a process death left open.
 *
 * @param backStack The navigator's back stack, a snapshot list, whose top a reopening races against.
 */
internal class SavedDialogRestorer(
    private val scope: CoroutineScope,
    private val savedStateStore: SavedStateStore,
    private val dialogHost: DialogHost,
    private val backStack: List<CampfireDestination>,
    private val songLookup: StateFlow<SongLookup>,
    private val setlists: StateFlow<List<Setlist>>,
    private val editorDraft: StateFlow<SongContent?>,
    private val songTexts: StateFlow<Map<String, String>>,
    private val loadSongContent: (fileName: String) -> Job,
    private val duplicateSongDialogOf: suspend (Song) -> DialogType?,
    private val showSavedSongEdit: (SavedDialog.SongEdit, Song) -> Unit,
) {

    private val _isSettled = MutableStateFlow(false)

    /**
     * Whether [start] is done: the saved sheet reopened, given up on, or never there. What a picker answered after a
     * process death waits for it, since the sheet it was picked for is not among the open dialogs before then.
     */
    val isSettled = _isSettled.asStateFlow()

    /**
     * Reopens the form sheet that was up when Android ended the process, see [SavedDialog], through the opener its menu
     * uses, so that the sheet composes where it did and its saved fields find it again. Not before the library has been
     * read and indexed and, for a sheet over a song's text, the text is held, since the openers read both; given up as
     * soon as the screen on top is another one, which a sheet put up later would cover by mistake. A sheet that cannot
     * be reopened takes its entry with it, so that the next process death does not try it again.
     */
    fun start() {
        val saved = savedStateStore.restore<SavedDialog?>(SavedStateStore.VISIBLE_DIALOG_KEY)
        if (saved == null) {
            _isSettled.value = true
            return
        }
        val top = backStack.lastOrNull()
        scope.launch {
            val isReopened = merge(
                flow { emit(reopen(saved, top)) },
                snapshotFlow { backStack.lastOrNull() }.filter { it != top }.map { false },
            ).first()
            if (!isReopened) savedStateStore.persist<SavedDialog?>(SavedStateStore.VISIBLE_DIALOG_KEY, null)
            _isSettled.value = true
        }
    }

    /**
     * Reopens the export screen of [target], which the start up that answers a consent page asked from it does not
     * bring back, once the library holds the item, on the Link format the consent was asked from, for that opening only.
     * Given up on as a saved sheet is: over another dialog, and once the screen on top is another one.
     */
    fun reopenExport(target: ShareTarget) {
        val top = backStack.lastOrNull()
        scope.launch {
            merge(
                flow { emit(showExport(target)) },
                snapshotFlow { backStack.lastOrNull() }.filter { it != top }.map { false },
            ).first()
        }
    }

    private suspend fun showExport(target: ShareTarget): Boolean {
        val lookup = songLookup.first { it.isLibraryRead }
        val dialog = when (target.kind) {
            SharedLinkKind.SONG -> lookup.songsByFileName[target.fileName]?.let { DialogType.Export(song = it, format = PrintSettings.Format.LINK) }
            SharedLinkKind.SETLIST -> setlists.value.firstOrNull { it.fileName == target.fileName }?.let { DialogType.Export(setlist = it, format = PrintSettings.Format.LINK) }
        } ?: return false
        if (dialogHost.visibleDialog.value != null) return false
        dialogHost.showDialog(dialog)
        return true
    }

    /** Called with the dialogs that are open after every change, bottom first. */
    fun onOpenDialogsChanged(after: List<DialogType>) {
        // Every change is written, so that a sheet that goes takes its entry with it.
        savedStateStore.persist(SavedStateStore.VISIBLE_DIALOG_KEY, savedDialogOf(after))
    }

    private suspend fun reopen(saved: SavedDialog, top: CampfireDestination?): Boolean {
        if (saved is SavedDialog.SongEdit && saved.isEditorDraft) {
            // The editor reports its text only once it has composed, which it may never do if it is not on top.
            if (top !is CampfireDestination.SongEditor || top.fileName != saved.songFileName) return false
        }
        // A picture's address is asked for the editor it is inserted into, which must be the one on top.
        if (saved is SavedDialog.PictureLink && (top !is CampfireDestination.SongEditor || top.fileName != saved.editorFileName)) return false
        val lookup = songLookup.first { it.isLibraryRead }
        fun songOf(fileName: String) = lookup.songsByFileName[fileName]
        fun setlistOf(fileName: String) = setlists.value.firstOrNull { it.fileName == fileName }
        if (saved is SavedDialog.SongEdit) {
            if (saved.isEditorDraft) {
                editorDraft.first { it?.fileName == saved.songFileName }
            } else {
                loadSongContent(saved.songFileName).join()
                if (songTexts.value[saved.songFileName] == null) return false
            }
        }
        // What's new or an import question go first.
        if (dialogHost.visibleDialog.value != null) return false
        when (saved) {
            SavedDialog.NewSong -> dialogHost.showDialog(DialogType.NewSong)
            is SavedDialog.DuplicateSong -> dialogHost.showDialog(duplicateSongDialogOf(songOf(saved.songFileName) ?: return false) ?: return false)
            SavedDialog.NewSetlist -> dialogHost.showDialog(DialogType.NewSetlist)
            is SavedDialog.EditSetlist -> dialogHost.showDialog(DialogType.EditSetlist(setlistOf(saved.setlistFileName) ?: return false))
            is SavedDialog.DuplicateSetlist -> dialogHost.showDialog(DialogType.DuplicateSetlist(setlistOf(saved.setlistFileName) ?: return false))
            is SavedDialog.SongPicker -> dialogHost.showDialog(DialogType.SongPicker(setlistOf(saved.setlistFileName) ?: return false))
            is SavedDialog.SetlistPicker -> dialogHost.showDialog(
                DialogType.SetlistPicker(song = songOf(saved.songFileName) ?: return false, setlistFileName = saved.setlistFileName),
            )
            is SavedDialog.SongEdit -> showSavedSongEdit(saved, songOf(saved.songFileName) ?: return false)
            is SavedDialog.PictureLink -> dialogHost.showDialog(DialogType.PictureLink(saved.editorFileName))
        }
        // An opener that found nothing to open on puts nothing up.
        return dialogHost.visibleDialog.value != null
    }

    init {
        dialogHost.addOpenDialogsChange { _, after -> onOpenDialogsChanged(after) }
    }
}
