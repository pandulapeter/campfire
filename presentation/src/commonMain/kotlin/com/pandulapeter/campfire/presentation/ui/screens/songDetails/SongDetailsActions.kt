/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_text_size
import com.pandulapeter.campfire.presentation.ui.components.menu.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.components.actions.SetlistAssignmentsButton
import com.pandulapeter.campfire.presentation.ui.components.actions.SongActionHandler
import com.pandulapeter.campfire.presentation.ui.components.actions.SongActions
import com.pandulapeter.campfire.presentation.ui.components.menu.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.actions.setlistAssignmentsAction
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.fontScale.FontScaleController
import kotlinx.coroutines.flow.StateFlow

/**
 * The song details screen's app bar actions: the key chip ([keyChip]), then read only mode's mark and menu, or the
 * song's setlists and the song's menu, which holds what the bar has no room for, the tuner ([tunerAction]), and at its
 * end the text size, which a pinch and the zoom keys reach as well.
 */
@Composable
internal fun RowScope.SongDetailsActions(
    keyChip: @Composable () -> Unit,
    fontScaleController: FontScaleController,
    songFileNamesInSetlistsFlow: StateFlow<Set<String>>,
    songActions: SongActionHandler,
    currentSong: Song?,
    setlistFileName: String?,
    isReadOnly: Boolean,
    isPerformanceModeEnabled: Boolean,
    isImportPreview: Boolean,
    areSetlistsEnabled: Boolean,
    showsSetlistAssignmentsInBar: Boolean,
    aboutSongAction: ActionsMenuItem?,
    tunerAction: ActionsMenuItem?,
    preferencesAction: ActionsMenuItem,
    onReadOnlyMarkClicked: () -> Unit,
) {
    val songFileNamesInSetlists by songFileNamesInSetlistsFlow.collectAsStateWithLifecycle()
    keyChip()
    // The text size is rarely changed, and a pinch and the zoom keys change it too, so it is always the menu's last row
    // rather than room taken from the title.
    val fontScaleMenuFooter = @Composable {
        MenuStepperRow(label = stringResource(Res.string.song_details_text_size)) {
            LiveFontScaleControls(fontScaleController = fontScaleController)
        }
    }
    // Read only mode's actions and the song's are the same two buttons, a mark (the lock, the setlists' star) and an
    // overflow button reaching into it, laid out to the same width. Switching the mode therefore only crossfades them in
    // place, end aligned: shrinking one set while the other expands would slide the buttons and the key chip before them
    // back and forth. The width is animated only where the two differ (the bar too narrow for the star, or a preview,
    // which has read only mode's menu but never the lock: nothing in it is the library's to change until Import,
    // whatever the mode), and an imported preview turning into its song's screen gains the song's actions, or the lock,
    // the same way.
    AnimatedContent(
        targetState = when {
            isImportPreview -> BarActions.PREVIEW
            isPerformanceModeEnabled -> BarActions.READ_ONLY_MODE
            else -> BarActions.SONG
        },
        contentAlignment = Alignment.CenterEnd,
        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
    ) { barActions ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (barActions != BarActions.SONG) {
                val showsReadOnlyMark = barActions == BarActions.READ_ONLY_MODE
                if (showsReadOnlyMark) {
                    ReadOnlyMark(onClick = onReadOnlyMarkClicked)
                }
                // Read only mode's menu: nothing in it changes the library, and nothing in it is ever let out as a button.
                ActionsMenu(
                    isAfterButton = showsReadOnlyMark,
                    menuFooter = fontScaleMenuFooter,
                    items = { listOfNotNull(aboutSongAction, tunerAction, preferencesAction) },
                )
            } else {
                currentSong?.let { song ->
                    SongActionsWithSetlists(
                        song = song,
                        isInSetlist = song.fileName in songFileNamesInSetlists,
                        songActions = songActions,
                        setlistFileName = setlistFileName,
                        // Read only mode also takes the star out of the bar: the leaving actions keep it while they
                        // fade, or its own shrinking would slide it sideways under the lock fading in.
                        isReadOnly = heldWhileLeaving(isReadOnly),
                        areSetlistsEnabled = areSetlistsEnabled,
                        showsSetlistAssignmentsInBar = heldWhileLeaving(showsSetlistAssignmentsInBar),
                        aboutSongAction = aboutSongAction,
                        tunerAction = tunerAction,
                        preferencesAction = preferencesAction,
                        menuFooter = fontScaleMenuFooter,
                    )
                }
            }
        }
    }
}

/**
 * [value] as it was when this content started leaving its `AnimatedContent`, and as it is while it is shown: the leaving
 * content is still recomposed with the values that replaced it, and it should fade out as it was, not change on its way.
 */
@Composable
private fun <T> AnimatedVisibilityScope.heldWhileLeaving(value: T): T {
    val held = remember { mutableStateOf(value) }
    val isLeaving = transition.targetState == EnterExitState.PostExit
    SideEffect {
        if (!isLeaving) held.value = value
    }
    return if (isLeaving) held.value else value
}

/**
 * Which actions the bar ends in: the same menu alone over an import preview (whose card's Import is the only thing that
 * makes its song editable, in read only mode too), read only mode's mark and menu, or the song's own.
 */
private enum class BarActions {
    PREVIEW,
    READ_ONLY_MODE,
    SONG,
}

/** The song's own actions: the setlists' star where the bar has room for it, and the song's menu. */
@Composable
private fun SongActionsWithSetlists(
    song: Song,
    isInSetlist: Boolean,
    songActions: SongActionHandler,
    setlistFileName: String?,
    isReadOnly: Boolean,
    areSetlistsEnabled: Boolean,
    showsSetlistAssignmentsInBar: Boolean,
    aboutSongAction: ActionsMenuItem?,
    tunerAction: ActionsMenuItem?,
    preferencesAction: ActionsMenuItem,
    menuFooter: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = showsSetlistAssignmentsInBar,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        SetlistAssignmentsButton(
            actions = songActions,
            song = song,
            isInSetlist = isInSetlist,
            setlistFileName = setlistFileName,
        )
    }
    // The overflow button reaches into the button before it, and only into a button: the key chip and the title are
    // kept clear of its touch target.
    val overflowOverlap by animateDpAsState(if (showsSetlistAssignmentsInBar) ACTION_BUTTON_OVERLAP else 0.dp)
    SongActions(
        modifier = Modifier.overlappingAction(start = overflowOverlap, end = 0.dp),
        actions = songActions,
        song = song,
        isDeletable = setlistFileName == null,
        isEditAndExportOnly = isReadOnly,
        setlistFileName = setlistFileName,
        isAfterButton = showsSetlistAssignmentsInBar,
        editShortcut = KeyShortcut.EDIT,
        exportShortcut = KeyShortcut.SAVE,
        leadingItems = {
            listOfNotNull(
                aboutSongAction,
                if (isReadOnly || showsSetlistAssignmentsInBar || !areSetlistsEnabled) {
                    null
                } else {
                    setlistAssignmentsAction(
                        actions = songActions,
                        song = song,
                        isInSetlist = isInSetlist,
                        setlistFileName = setlistFileName,
                    )
                },
                tunerAction,
                preferencesAction,
            )
        },
        // The transposition and the capo are set from the key chip and the tempo in the dock under the song; what is
        // left for the menu is the text size, which belongs to the reader rather than to the song.
        menuFooter = menuFooter,
    )
}
