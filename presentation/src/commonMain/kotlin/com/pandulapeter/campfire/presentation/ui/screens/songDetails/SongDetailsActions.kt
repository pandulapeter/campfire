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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_text_size
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.components.SetlistAssignmentsButton
import com.pandulapeter.campfire.presentation.ui.components.SongActionHandler
import com.pandulapeter.campfire.presentation.ui.components.SongActions
import com.pandulapeter.campfire.presentation.ui.components.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.setlistAssignmentsAction
import com.pandulapeter.campfire.presentation.ui.tuner.tunerAction

/**
 * The song details screen's app bar actions: the text size where the bar has room for it, the song's setlists, and the
 * song's menu, which holds what the bar has no room for.
 */
@Composable
internal fun RowScope.SongDetailsActions(
    viewModel: CampfireViewModel,
    songActions: SongActionHandler,
    currentSong: Song?,
    setlistFileName: String?,
    isReadOnly: Boolean,
    isPerformanceModeEnabled: Boolean,
    areSetlistsEnabled: Boolean,
    showsFontScaleInBar: Boolean,
    showsSetlistAssignmentsInBar: Boolean,
    tunerAction: ActionsMenuItem?,
) {
    val songFileNamesInSetlists by viewModel.songFileNamesInSetlists.collectAsStateWithLifecycle()
    // The text size is in the bar wherever the title leaves it the room, and at the end of a menu where not:
    // performance mode leaves the bar with nothing else in it, and elsewhere it is the first of the bar's
    // controls to go back into the menu as the bar narrows. Both are there, one leaving as the other arrives,
    // so that a window resized across the width that decides it hands the stepper over the way the song's
    // actions move in and out of their menu.
    AnimatedVisibility(
        visible = showsFontScaleInBar,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionsMenu(items = listOfNotNull(tunerAction?.takeIf { isPerformanceModeEnabled }))
            LiveFontScaleControls(
                modifier = Modifier.padding(end = if (isReadOnly) APP_BAR_STEPPER_END_PADDING else 0.dp),
                viewModel = viewModel,
            )
        }
    }
    // A song read from an archived setlist keeps its own menu, so there the song's Edit and Export take these in
    // rather than a second overflow button standing next to this one.
    AnimatedVisibility(
        visible = isPerformanceModeEnabled && !showsFontScaleInBar,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        ActionsMenu(
            items = listOfNotNull(tunerAction),
            menuFooter = {
                MenuStepperRow(label = stringResource(Res.string.song_details_text_size)) {
                    LiveFontScaleControls(viewModel = viewModel)
                }
            },
        )
    }
    currentSong?.takeIf { !isPerformanceModeEnabled }?.let { song ->
        val isInSetlist = song.fileName in songFileNamesInSetlists
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
        // The overflow button reaches into the button before it, and only into a button: the stepper's pill and
        // the title are kept clear of its touch target.
        val overflowOverlap by animateDpAsState(if (showsSetlistAssignmentsInBar) ACTION_BUTTON_OVERLAP else 0.dp)
        SongActions(
            modifier = Modifier.overlappingAction(start = overflowOverlap, end = 0.dp),
            actions = songActions,
            song = song,
            isDeletable = setlistFileName == null,
            isEditAndExportOnly = isReadOnly,
            setlistFileName = setlistFileName,
            leadingItems = listOfNotNull(
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
            ),
            // The transposition, the capo and the tempo are set in the song's own first section now; what
            // is left for the menu is the text size, which belongs to the reader rather than to the song.
            menuFooter = if (showsFontScaleInBar) {
                null
            } else {
                {
                    MenuStepperRow(label = stringResource(Res.string.song_details_text_size)) {
                        LiveFontScaleControls(viewModel = viewModel)
                    }
                }
            },
        )
    }
}
