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

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.components.OverlayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The dialog or sheet on screen, and what is open over the app besides it ([overlayState]). Whatever else a dialog
 * change does - ending the setlist reorder mode, answering an exit parked behind a question, saving the export screen's
 * options - is registered by the owner as a listener, in the order it has to happen in, see [addBeforeDialogChange].
 */
internal class DialogHost(
    private val scope: CoroutineScope,
) {

    /** Whether a menu is open over the app, which CampfireApp hands to every menu it composes. */
    val overlayState = OverlayState()

    private val _visibleDialog = MutableStateFlow<DialogType?>(null)
    val visibleDialog: StateFlow<DialogType?> = _visibleDialog.asStateFlow()

    // Kept mounted beneath an editor so its sheet and scroll position survive opening and closing that editor.
    private val _underlyingSongInfo = MutableStateFlow<DialogType.SongInfo?>(null)
    val underlyingSongInfo = _underlyingSongInfo.asStateFlow()

    private val beforeDialogChangeListeners = mutableListOf<(previous: DialogType?, next: DialogType?) -> Unit>()
    private val afterDialogChangeListeners = mutableListOf<(previous: DialogType?, next: DialogType?) -> Unit>()

    /** Called before every change of [visibleDialog], in the order the listeners were added. */
    fun addBeforeDialogChange(listener: (previous: DialogType?, next: DialogType?) -> Unit) {
        beforeDialogChangeListeners += listener
    }

    /** Called after every change of [visibleDialog], in the order the listeners were added. */
    fun addAfterDialogChange(listener: (previous: DialogType?, next: DialogType?) -> Unit) {
        afterDialogChangeListeners += listener
    }

    /**
     * Puts [dialogType] up only where nothing is on screen, and answers whether it did. Not through [setVisibleDialog]:
     * this only ever replaces no dialog at all, behind which nothing is parked.
     */
    fun showIfNoneIsShown(dialogType: DialogType) = _visibleDialog.compareAndSet(null, dialogType)

    /**
     * A sheet or a dialog about one song goes when the song does - deleted or renamed by a sync run, or taken out
     * of the folder behind the app's back - whichever screen opened it: the details screen underneath closes
     * itself, but the dialogs are not its own, and a setlist picker left behind would write the name of a file that
     * is not there into every setlist ticked in it. Only against a library that has been read, and never for a song
     * this app is renaming, which is missing from the library for a few writes on purpose ([songsBeingRenamed]).
     */
    fun startClosingWithSong(
        allSongs: Flow<List<Song>>,
        isLoading: Flow<Boolean>,
        songsBeingRenamed: Flow<Map<String, Song>>,
    ) = scope.launch {
        combine(_visibleDialog, allSongs, isLoading, songsBeingRenamed) { dialog, songs, isLoading, songsBeingRenamed ->
            val fileName = dialog?.songFileName
            dialog?.takeIf { fileName != null && !isLoading && fileName !in songsBeingRenamed && songs.none { it.fileName == fileName } }
        }.filterNotNull().collect { dialog ->
            // The song disappearing closes both the editor and the sheet underneath it.
            if (_visibleDialog.value == dialog) setVisibleDialog(null)
        }
    }

    /**
     * The one place [visibleDialog] is given a value, because a dialog can have work parked behind it that nothing
     * else can answer for: the exit behind [DialogType.UnsavedChanges]. It goes with its dialog, however that leaves
     * the screen - answered, dismissed, or replaced, the way the desktop's close button puts the unsaved changes
     * question over anything. That work, and everything else a change of dialog sets off, is in the listeners.
     */
    fun setVisibleDialog(dialogType: DialogType?) {
        val previousDialog = _visibleDialog.value
        beforeDialogChangeListeners.forEach { it(previousDialog, dialogType) }
        _underlyingSongInfo.value = when {
            // The cover art sheet's Remove asks first, and either answer goes back to the sheet the cover art was opened
            // from: Remove directly, Cancel through the cover art sheet it puts back.
            previousDialog is DialogType.CoverArtSearch && dialogType is DialogType.RemoveSongCoverArt -> _underlyingSongInfo.value
            previousDialog is DialogType.RemoveSongCoverArt && dialogType is DialogType.CoverArtSearch -> _underlyingSongInfo.value
            else -> (previousDialog as? DialogType.SongInfo)
        }?.takeIf { parent ->
            dialogType is DialogType.SongEdit && dialogType.target is SongEditTarget.File && dialogType.song.fileName == parent.song.fileName &&
                (dialogType is DialogType.SongMetadata || dialogType is DialogType.SongTags ||
                    dialogType is DialogType.SongLinks || dialogType is DialogType.SongLanguages ||
                    dialogType is DialogType.SongPlaying || dialogType is DialogType.CoverArtSearch ||
                    dialogType is DialogType.RemoveSongCoverArt)
        }
        _visibleDialog.update { dialogType }
        afterDialogChangeListeners.forEach { it(previousDialog, dialogType) }
    }

    fun showDialog(dialogType: DialogType) = setVisibleDialog(dialogType)

    fun dismissDialog() = setVisibleDialog(_underlyingSongInfo.value)

    /**
     * What a bottom sheet dismisses itself with: [dialogType] goes only while it is still the dialog on screen. A
     * sheet reports its dismissal from the end of its hide animation, and one that is replaced while it is hiding
     * reports the cancellation of that animation the same way - Material's scrim and back handlers included - by
     * which time the dialog on screen is the one that replaced it. It is also how the export screen closes itself, for
     * the same reason: its saved file and its back gesture can both arrive once another dialog has replaced it.
     */
    fun dismissSheet(dialogType: DialogType) {
        if (_visibleDialog.value == dialogType) dismissDialog()
    }

    /** The song a dialog is about, for the ones that are about one, see [startClosingWithSong]. */
    private val DialogType.songFileName: String?
        get() = when (this) {
            is DialogType.SetlistPicker -> song.fileName
            is DialogType.DeleteSong -> song.fileName
            is DialogType.SongInfo -> song.fileName
            is DialogType.ChordShapes -> song.fileName
            // The editor's draft is the editor's to keep, whatever became of the file it was opened on.
            is DialogType.SongEdit -> song.fileName.takeIf { target is SongEditTarget.File }
            else -> null
        }
}
