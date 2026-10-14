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

import com.pandulapeter.campfire.presentation.ui.dialogs.pickers.SetlistPicker
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.components.menu.OverlayState
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

    /**
     * Every dialog open: [visibleDialog] last, and under it the ones it was opened from and stacks over (see
     * [stacksOver]), the one at the bottom first. Those stay composed underneath it, so that closing it only slides it
     * away and uncovers them as they were — their drafts, their scroll positions — rather than putting them up again,
     * sliding in as if they had just been opened. One flow rather than the ones under it apart from [visibleDialog], so
     * that what is drawn never sees one of the two changed and not the other.
     */
    private val _openDialogs = MutableStateFlow<List<DialogType>>(emptyList())
    val openDialogs: StateFlow<List<DialogType>> = _openDialogs.asStateFlow()

    /**
     * An open dialog asked to close itself rather than be taken off the stack, see [dismissDialogAndSheetUnder]: a
     * sheet taken off is gone in one frame, and one that closes itself slides away first. Cleared by the next change.
     */
    private val _closeRequest = MutableStateFlow<DialogType?>(null)
    val closeRequest: StateFlow<DialogType?> = _closeRequest.asStateFlow()

    private val beforeDialogChangeListeners = mutableListOf<(previous: DialogType?, next: DialogType?) -> Unit>()
    private val afterDialogChangeListeners = mutableListOf<(previous: DialogType?, next: DialogType?) -> Unit>()
    private val openDialogsChangeListeners = mutableListOf<(before: List<DialogType>, after: List<DialogType>) -> Unit>()

    /** Called before every change of [visibleDialog], in the order the listeners were added. */
    fun addBeforeDialogChange(listener: (previous: DialogType?, next: DialogType?) -> Unit) {
        beforeDialogChangeListeners += listener
    }

    /** Called after every change of [visibleDialog], in the order the listeners were added. */
    fun addAfterDialogChange(listener: (previous: DialogType?, next: DialogType?) -> Unit) {
        afterDialogChangeListeners += listener
    }

    /**
     * Called after every change of [visibleDialog] with every dialog open before it and after it ([openDialogs]), for
     * what belongs to a dialog for as long as it is open anywhere in the stack rather than only while it is on top.
     */
    fun addOpenDialogsChange(listener: (before: List<DialogType>, after: List<DialogType>) -> Unit) {
        openDialogsChangeListeners += listener
    }

    /**
     * Puts [dialogType] up only where nothing is on screen, and answers whether it did. Not through [setVisibleDialog]:
     * this only ever replaces no dialog at all, behind which nothing is parked.
     */
    fun showIfNoneIsShown(dialogType: DialogType) = _visibleDialog.compareAndSet(null, dialogType).also { isShown ->
        if (isShown) _openDialogs.value = listOf(dialogType)
    }

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
            // The song disappearing closes the whole stack, every dialog in which is about that song.
            if (_visibleDialog.value == dialog) setVisibleDialog(null)
        }
    }

    /**
     * The one place [visibleDialog] is given a value, because a dialog can have work parked behind it that nothing
     * else can answer for: the exit behind [DialogType.UnsavedChanges]. It goes with its dialog, however that leaves
     * the screen - answered, dismissed, or replaced, the way the desktop's close button puts the unsaved changes
     * question over anything. That work, and everything else a change of dialog sets off, is in the listeners.
     *
     * The stack under it follows from [dialogType] ([nextUnderlyingDialogs]): one in the place ([slotKey]) of a dialog
     * already open goes back to that one, closing everything above it and taking its place; one that [stacksOver] an
     * open dialog is put over it; anything else replaces the whole stack.
     */
    fun setVisibleDialog(dialogType: DialogType?) {
        val previousDialog = _visibleDialog.value
        val previousOpenDialogs = _openDialogs.value
        beforeDialogChangeListeners.forEach { it(previousDialog, dialogType) }
        _closeRequest.value = null
        _openDialogs.value = nextUnderlyingDialogs(openDialogs = previousOpenDialogs, next = dialogType) + listOfNotNull(dialogType)
        _visibleDialog.update { dialogType }
        afterDialogChangeListeners.forEach { it(previousDialog, dialogType) }
        openDialogsChangeListeners.forEach { it(previousOpenDialogs, _openDialogs.value) }
    }

    fun showDialog(dialogType: DialogType) = setVisibleDialog(dialogType)

    /** Closes the dialog on screen, uncovering the one it was put over, if any. */
    fun dismissDialog() = _openDialogs.value.let { open -> setVisibleDialog(open.getOrNull(open.lastIndex - 1)) }

    /**
     * Closes the dialog on screen and asks the sheet it was put over to close itself too ([closeRequest]), which a
     * `CampfireBottomSheet` does with its slide and then dismisses itself as any closed sheet does: for a question
     * whose answer leaves nothing for the sheet under it to do.
     */
    fun dismissDialogAndSheetUnder() {
        val sheet = _openDialogs.value.let { open -> open.getOrNull(open.lastIndex - 1) } ?: return setVisibleDialog(null)
        setVisibleDialog(sheet)
        _closeRequest.value = sheet
    }

    /**
     * Puts [dialog] in the place ([slotKey]) of the open dialog under the one on screen that has it, which is updated
     * where it is rather than put up again: the metadata form the song details lookup fills in, still under the lookup
     * as it slides away.
     */
    fun updateUnderlyingDialog(dialog: DialogType) {
        _openDialogs.update { open ->
            open.mapIndexed { index, openDialog -> if (index < open.lastIndex && openDialog.slotKey == dialog.slotKey) dialog else openDialog }
        }
    }

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
            is DialogType.SongInfo -> song.fileName.takeIf { target is SongEditTarget.File }
            is DialogType.ChordShape -> song.fileName
            // The editor's draft is the editor's to keep, whatever became of the file it was opened on.
            is DialogType.SongEdit -> song.fileName.takeIf { target is SongEditTarget.File }
            else -> null
        }
}
