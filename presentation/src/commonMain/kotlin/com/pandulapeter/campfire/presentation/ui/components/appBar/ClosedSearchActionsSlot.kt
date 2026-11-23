/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.appBar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.menu.overlappingAction
import kotlin.math.roundToInt

/**
 * The closed search's own actions at the end of [SearchableTopAppBar]'s buttons (New, or the selection menu), with the
 * divider before them: they come and go as the screen offers them ([isShown]), and make way for the field as the search
 * opens unless [isKeptInSearch], on the search action's spring.
 *
 * @param lookaheadScope The bar's own, which their room is measured in for the pill (`SearchPill.closedSearchActionsWidth`).
 * @param pillProgress How far the search is open, from 0 while it is closed to 1 while it is open.
 * @param detachProgress How far the actions are detached from the pill, from 0 to 1.
 * @param isDetached Whether they stand beside the pill rather than on it, see `areClosedSearchActionsDetached`.
 */
@Composable
internal fun RowScope.ClosedSearchActionsSlot(
    lookaheadScope: LookaheadScope,
    pill: SearchPill,
    searchTransition: Transition<Boolean>,
    pillProgress: () -> Float,
    detachProgress: () -> Float,
    isShown: Boolean,
    isKeptInSearch: Boolean,
    isDetached: Boolean,
    content: @Composable RowScope.() -> Unit,
) = AnimatedVisibility(
    modifier = Modifier.onPlaced {
        if (!searchTransition.targetState) {
            pill.closedSearchActionsWidth =
                lookaheadScope.closedBoundsOf(pill.bar, it, isClosing = searchTransition.currentState).width
        }
    },
    visible = isShown,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClosedSearchActionsDivider(
            pill = pill,
            searchProgress = { if (isKeptInSearch) 0f else pillProgress() },
            detachProgress = detachProgress,
        )
        val closedSearchActionsSizeSpec = searchTravelSpec(visibilityThreshold = IntSize.VisibilityThreshold)
        val closedSearchActionsFadeSpec = searchTravelSpec<Float>()
        searchTransition.AnimatedVisibility(
            modifier = Modifier.overlappingAction(),
            visible = { !it || isKeptInSearch },
            // A detached button is a filled circle, which a clipped shrink would slice into a half-moon on
            // its way out: it shrinks in place instead, while its room closes behind the field's edge.
            enter = fadeIn(closedSearchActionsFadeSpec) + if (isDetached) {
                scaleIn(closedSearchActionsFadeSpec, initialScale = DETACHED_ACTION_HIDDEN_SCALE) +
                    expandHorizontally(closedSearchActionsSizeSpec, clip = false)
            } else {
                expandHorizontally(closedSearchActionsSizeSpec)
            },
            // Faded out sooner than the room closes, too, or the actions sliding into its place pass
            // under what is left of the disc.
            exit = if (isDetached) {
                fadeOut(tween(DETACHED_ACTION_FADE_OUT_MILLIS)) +
                    scaleOut(closedSearchActionsFadeSpec, targetScale = DETACHED_ACTION_HIDDEN_SCALE) +
                    shrinkHorizontally(closedSearchActionsSizeSpec, clip = false)
            } else {
                fadeOut(closedSearchActionsFadeSpec) + shrinkHorizontally(closedSearchActionsSizeSpec)
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                content()
            }
        }
    }
}

/**
 * The line that sets the closed search's own actions apart from the rest of the bar's: those change how the list is
 * shown, while these add to what it holds.
 *
 * On the pill it takes a little room of its own ([CLOSED_SEARCH_ACTIONS_DIVIDER_ROOM]), which the shares of the two
 * buttons it divides stop at (see [SearchPill.divider]), so that neither's press runs over the line. Before detached actions it fades out and its room widens into the gap
 * between the pill's end and theirs (see [DETACHED_CLOSED_SEARCH_ACTIONS_GAP]). It narrows and fades on the search
 * action's spring rather than inside the animation of the actions it stands in front of, which shrink towards their
 * end edge clipped to their bounds and would cut it off while the button behind it was still fading, and it is drawn
 * centered in its room, never clipped.
 *
 * @param searchProgress How far the search is open, from 0 while it is closed to 1 while it is open.
 * @param detachProgress How far the actions after it are detached from the pill, from 0 to 1.
 */
@Composable
private fun ClosedSearchActionsDivider(
    pill: SearchPill,
    searchProgress: () -> Float,
    detachProgress: () -> Float,
) {
    DisposableEffect(pill) {
        onDispose { pill.divider = null }
    }
    VerticalDivider(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val room = CLOSED_SEARCH_ACTIONS_DIVIDER_ROOM.toPx()
                val closedWidth = room + (DETACHED_CLOSED_SEARCH_ACTIONS_GAP.toPx() - room) * detachProgress()
                val width = (closedWidth * (1f - searchProgress())).roundToInt().coerceAtLeast(0)
                layout(width, placeable.height) {
                    placeable.placeRelative(x = (width - placeable.width) / 2, y = 0)
                }
            }
            .onGloballyPositioned { coordinates ->
                pill.bar?.takeIf { it.isAttached }?.let { pill.divider = it.localBoundingBoxOf(coordinates, clipBounds = false) }
            }
            .graphicsLayer { alpha = ((1f - searchProgress()) * (1f - detachProgress())).coerceIn(0f, 1f) }
            .height(CLOSED_SEARCH_ACTIONS_DIVIDER_HEIGHT),
    )
}

/** The length of [ClosedSearchActionsDivider], short of the buttons' height so that it reads as a separator between them. */
private val CLOSED_SEARCH_ACTIONS_DIVIDER_HEIGHT = 24.dp

/**
 * The room [ClosedSearchActionsDivider] takes on the pill, the line in the middle of it: what keeps the presses of the
 * buttons on either side off the line, and the button before it in line with the checkboxes of a selection.
 */
private val CLOSED_SEARCH_ACTIONS_DIVIDER_ROOM = 4.dp

/** How small a detached action is as it fades out of the bar and back in, see `areClosedSearchActionsDetached`. */
private const val DETACHED_ACTION_HIDDEN_SCALE = 0.6f

/** How long a detached action takes to fade out of the bar as the search opens. */
private const val DETACHED_ACTION_FADE_OUT_MILLIS = 120
