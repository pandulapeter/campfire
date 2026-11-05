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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.components.actions.SongActionHandler
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.PageStepper
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * [songKeyboardShortcuts] as the song details screen sets them up: the arrows scroll the song being read, and Up and
 * Down do whatever the step buttons would do where they are there, so that a pedal pressing them reads the song the way
 * the buttons do; where they are not, the keys scroll. Not part of [SongDetailsScreen] itself, so that a change of what
 * it reads recomposes only this.
 *
 * @param hasPreviousSong Whether there is a song before the one being headed for. The target page only decides whether
 *   the key does anything; the step itself is decided at the time of the press.
 * @param librarySong The song being read where its menu has Edit and Share or export, which Ctrl / Cmd + E and S then
 *   open: not in read only mode, nor in a preview, whose Ctrl / Cmd + S presses its import button instead.
 */
@Composable
internal fun Modifier.songDetailsKeyboardShortcuts(
    currentPageScrollState: () -> ScrollState?,
    coroutineScope: CoroutineScope,
    pageStepper: PageStepper,
    canStepBack: Boolean,
    canStepForward: Boolean,
    hasPreviousSong: Boolean,
    hasNextSong: Boolean,
    isUncovered: Boolean,
    stepBack: (from: Int?) -> Unit,
    stepForward: (from: Int?) -> Unit,
    onToggleAutoScroll: (() -> Unit)?,
    songActions: SongActionHandler,
    librarySong: Song?,
    setlistFileName: String?,
    importPreview: ImportPreviewState?,
    importController: ImportController,
) = songKeyboardShortcuts(
    onScrollUp = { currentPageScrollState()?.let { coroutineScope.launch { it.scrollByKeyStep(-1f) } } },
    onScrollDown = { currentPageScrollState()?.let { coroutineScope.launch { it.scrollByKeyStep(1f) } } },
    onStepBack = if (canStepBack) {
        { stepBack(null) }
    } else {
        null
    },
    onStepForward = if (canStepForward) {
        { stepForward(null) }
    } else {
        null
    },
    isUncovered = isUncovered,
    onToggleAutoScroll = onToggleAutoScroll,
    onEdit = librarySong?.let { song -> { songActions.edit(song) } },
    onSave = importPreview?.let { preview ->
        // The batch being added is waited for by the controller itself, which ignores the press meanwhile.
        preview.actions?.takeIf { preview.canImport(primary = it.primary, isAddingToReview = false) }?.saveShortcutAction
    }?.let { action -> { importController.importFromPreview(action) } } ?: librarySong?.let { song -> { songActions.export(song, setlistFileName) } },
    onPreviousSong = if (hasPreviousSong) {
        { pageStepper.step(-1) }
    } else {
        null
    },
    onNextSong = if (hasNextSong) {
        { pageStepper.step(1) }
    } else {
        null
    },
)

/**
 * One press of an arrow key, in the direction it was pressed (-1 for up, 1 for down). The step is a fraction of what
 * is on screen rather than a fixed distance, so it means the same thing on a phone and on a full screen window, and
 * it is animated over roughly the interval a held key repeats at: a single press then reads as one smooth nudge,
 * while a held one keeps restarting an animation that is still moving and scrolls at a steady pace instead of
 * stuttering between steps.
 */
private suspend fun ScrollState.scrollByKeyStep(direction: Float) = animateScrollBy(
    value = viewportSize * KEY_SCROLL_STEP_FRACTION * direction,
    animationSpec = tween(durationMillis = KEY_SCROLL_STEP_DURATION, easing = LinearEasing),
)

private const val KEY_SCROLL_STEP_FRACTION = 0.1f // Of the height of the scrolling viewport.
private const val KEY_SCROLL_STEP_DURATION = 120
