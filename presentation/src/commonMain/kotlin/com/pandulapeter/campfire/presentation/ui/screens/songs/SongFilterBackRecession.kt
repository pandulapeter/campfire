/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.collectLatest

/**
 * Makes the first back on a songs screen with nothing under it reset the tag and language filters rather than leave the
 * app, as the desktop's Escape does (`EscapeStep.CLEAR_FILTERS`), which never reaches this: its window key handler sees
 * the key before Compose turns it into a back event.
 *
 * Returns how far the filter action's badge and fill have receded, from 0 to 1, for them to be drawn faded by: a
 * predictive back gesture takes them away with the finger, so the drag says what letting go will do. A gesture let go
 * of without completing brings them back; one that completed leaves them where the gesture took them for their own
 * exit to carry on from, and it is only forgotten once a filter is on again.
 */
@Composable
internal fun songFilterBackRecession(
    isBackEnabled: Boolean,
    isSongFilterActive: Boolean,
    onBackCompleted: () -> Unit,
): () -> Float {
    val gesture = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    // The filter is a state flow derived from the selection, so it is still on for a moment after the reset: the
    // gesture's own completion is what tells the badge and fill not to come back.
    var isCompleted by remember { mutableStateOf(false) }
    // Registered whether or not it is enabled, since a handler that comes and goes changes the order the dispatcher
    // picks between handlers in.
    NavigationBackHandler(
        state = gesture,
        isBackEnabled = isBackEnabled,
        onBackCompleted = {
            isCompleted = true
            onBackCompleted()
        },
    )
    val recession = remember { Animatable(0f) }
    val returnSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(isSongFilterActive) {
        if (isSongFilterActive) {
            isCompleted = false
            recession.snapTo(0f)
        }
    }
    LaunchedEffect(gesture) {
        snapshotFlow {
            (gesture.transitionState as? NavigationEventTransitionState.InProgress)
                ?.takeIf { it.direction == NavigationEventTransitionState.TRANSITIONING_BACK }
                ?.latestEvent
                ?.progress
        }.collectLatest { progress ->
            when {
                progress != null -> recession.snapTo(progress)
                !isCompleted -> recession.animateTo(targetValue = 0f, animationSpec = returnSpec)
            }
        }
    }
    return remember(recession) { { recession.value } }
}
