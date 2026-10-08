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

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.slideFractionSpec
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

/**
 * How far [ExportHost] has dealt the export screen over the app, which the screens under it follow the way they
 * follow a destination being pushed over them: 0 while it is away, 1 while it covers them.
 */
@Stable
internal class ExportTransition {

    val progress = Animatable(0f)
}

/**
 * Puts [ExportScreen] over the whole app for as long as the view model's dialog is an export, dealing it in from
 * the right edge and taking it away again with the spring, the direction and the background slide of the app's own
 * navigation, and following a predictive back gesture as a destination's pop does. It is drawn inside the app's own
 * layout rather than in a window of its own, so that the snackbars of a failed export still show over it; it is still a
 * dialog to the view model, which is what lets the desktop's Escape, the web's Back and another dialog put up in its
 * place close it the way they close a sheet.
 */
@Composable
internal fun ExportHost(
    viewModel: CampfireViewModel,
    transition: ExportTransition,
) {
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val target = visibleDialog as? DialogType.Export
    // The export on screen, which outlives the dialog for as long as the screen takes to slide away. One export
    // replacing another is the same screen with what it reads starting over, not a second one sliding in.
    var shown by remember { mutableStateOf<DialogType.Export?>(null) }
    // The export a back gesture has just closed: the gesture ends a moment before the dialog does, and the screen
    // slides on away from wherever the finger left it rather than starting back towards the open position first.
    var closedByGesture by remember { mutableStateOf<DialogType.Export?>(null) }
    val backGesture = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    // Composed after the app, so that the dispatcher reaches this handler before the back stack's, and registered
    // whether or not the screen is up, since a handler that comes and goes changes the order the dispatcher picks in.
    NavigationBackHandler(
        state = backGesture,
        isBackEnabled = target != null,
        onBackCompleted = {
            closedByGesture = target
            target?.let(viewModel::dismissSheet)
        },
    )
    val spec = MaterialTheme.motionScheme.slideFractionSpec()
    LaunchedEffect(transition) {
        snapshotFlow {
            val gestureProgress = (backGesture.transitionState as? NavigationEventTransitionState.InProgress)
                ?.takeIf { it.direction == NavigationEventTransitionState.TRANSITIONING_BACK }
                ?.latestEvent
                ?.progress
            (visibleDialog as? DialogType.Export)?.takeIf { it !== closedByGesture } to gestureProgress
        }.collectLatest { (open, gestureProgress) ->
            when {
                open == null -> {
                    transition.progress.animateTo(0f, spec)
                    shown = null
                    closedByGesture = null
                }
                // The finger moves the screen itself, the way it moves a destination, rather than a spring chasing it.
                gestureProgress != null -> transition.progress.snapTo(1f - gestureProgress)
                else -> {
                    shown = open
                    transition.progress.animateTo(1f, spec)
                }
            }
        }
    }
    shown?.let { dialog ->
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = ((1f - transition.progress.value) * size.width).roundToInt().toFloat() },
        ) {
            ExportScreen(viewModel, dialog)
        }
    }
}
