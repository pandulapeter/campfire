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
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import com.pandulapeter.campfire.presentation.ui.state.SongLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
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

    /**
     * Reopens the form sheet that was up when Android ended the process, see [SavedDialog], through the opener its menu
     * uses, so that the sheet composes where it did and its saved fields find it again. Not before the library has been
     * read and indexed and, for a sheet over a song's text, the text is held, since the openers read both; given up as
     * soon as the screen on top is another one, which a sheet put up later would cover by mistake. A sheet that cannot
     * be reopened takes its entry with it, so that the next process death does not try it again.
     */
    fun start() {
        val saved = savedStateStore.restore<SavedDialog?>(SavedStateStore.VISIBLE_DIALOG_KEY) ?: return
        val top = backStack.lastOrNull()
        scope.launch {
            val isReopened = merge(
                flow { emit(reopen(saved, top)) },
                snapshotFlow { backStack.lastOrNull() }.filter { it != top }.map { false },
            ).first()
            if (!isReopened) savedStateStore.persist<SavedDialog?>(SavedStateStore.VISIBLE_DIALOG_KEY, null)
        }
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
        }
        // An opener that found nothing to open on puts nothing up.
        return dialogHost.visibleDialog.value != null
    }
}
