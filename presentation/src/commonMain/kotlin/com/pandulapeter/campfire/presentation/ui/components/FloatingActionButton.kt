/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sign

/**
 * The primary action of a list screen, dealt in and out with the screen it belongs to rather than living in the app's
 * chrome, so that it arrives with the screen it acts on instead of animating on its own while the screen slides.
 *
 * Wide windows get the label next to the icon; on a phone it would take a third of the row, so there it stays the
 * icon's content description. Every screen goes through this one button, so they can never drift apart.
 *
 * It slides off the bottom edge while [listState] is being scrolled downwards and comes back the moment the list is
 * scrolled upwards, which is what keeps it out of the way of the fast scroller running down the same corner of the
 * song list: a reader on their way down the library is not reaching for it, and one on their way back up may well be.
 * [isPushedAway] puts it away for a reason of the screen's own on top of that, which on the song list is the fast
 * scroller's thumb being held: the thumb can be taken to the bottom of its track without the list scrolling anywhere
 * near far enough to have moved the button, and the finger would arrive on top of it.
 *
 * The slide is a translation of the whole button and its margins, so what leaves the screen is the button in its
 * entirety rather than a shape hanging off the edge, and it is kept apart from [isVisible] so that the reasons the
 * button is taken away altogether still animate the way they did.
 *
 * @param isPushedAway Puts the button away and nothing more: letting go does not bring it back, since the finger that
 * let go is still over the corner it would come back into. Taking the list back up is what asks for it again.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CampfireFloatingActionButton(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    isPushedAway: Boolean = false,
    listState: LazyGridState,
    settledWidth: Dp,
    contentPadding: PaddingValues,
    icon: Painter,
    label: String,
    onClick: () -> Unit,
) {
    var slideDistance by remember { mutableIntStateOf(0) }
    val isSlidAway = rememberIsSlidAway(listState = listState, isPushedAway = isPushedAway)
    val slideProgress by animateFloatAsState(
        targetValue = if (isSlidAway) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    )
    AnimatedVisibility(
        // The size is taken with the margins included, since that is what the button has to travel to clear the
        // bottom edge of the window: the gap it keeps from that edge is as much a part of it as its own height.
        modifier = modifier
            .onSizeChanged { slideDistance = it.height }
            .graphicsLayer { translationY = slideDistance * slideProgress }
            .padding(
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current) + FAB_MARGIN,
                bottom = contentPadding.calculateBottomPadding() + FAB_MARGIN,
            ),
        visible = isVisible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        val isExtended = settledWidth >= EXTENDED_FAB_MIN_WIDTH
        val iconContent = @Composable {
            Icon(
                painter = icon,
                contentDescription = if (isExtended) null else label,
            )
        }
        if (isExtended) {
            ExtendedFloatingActionButton(
                onClick = onClick,
                icon = iconContent,
                text = { Text(label) },
            )
        } else {
            FloatingActionButton(onClick = onClick) { iconContent() }
        }
    }
}

/**
 * Whether the button is off the screen: either the list has been taken downwards far enough to put it there, or the
 * screen has put it there through [isPushedAway].
 *
 * The direction is worked out from the position the list reports rather than from a nested scroll connection, so that
 * the scrolls nobody's finger performed on the list itself count as well: the jump back to the top a new search query
 * makes, and the fast scroller - which is also why a push takes the list's own travel out of the reckoning for as long
 * as it lasts, since what moves the list then is the thumb rather than the reader. Travel is otherwise accumulated
 * until it passes [SLIDE_THRESHOLD] and starts over whenever it reverses, so that the few pixels a finger resting on
 * the list moves it cannot flip the button.
 *
 * A push is one directional: it puts the button away and letting go leaves it there, because the finger that let go
 * of the thumb is still over the corner the button would come back into. What brings it back is the list being taken
 * upwards afterwards - or reaching the top, which is at once the one place an upward scroll can no longer be asked
 * for and the place the button's own action is likeliest to be wanted.
 */
@Composable
private fun rememberIsSlidAway(
    listState: LazyGridState,
    isPushedAway: Boolean,
): Boolean {
    val threshold = with(LocalDensity.current) { SLIDE_THRESHOLD.toPx() }
    val pushedAway by rememberUpdatedState(isPushedAway)
    var isSlidAway by remember(listState) { mutableStateOf(false) }
    LaunchedEffect(listState, threshold) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        var travel = 0f
        snapshotFlow {
            SlideInput(
                index = listState.firstVisibleItemIndex,
                offset = listState.firstVisibleItemScrollOffset,
                isAtTop = !listState.canScrollBackward,
                isPushedAway = pushedAway,
            )
        }.collect { input ->
            // A different item at the top means at least a whole row went past, which is more than the threshold
            // either way, and the offset that came with it measures a distance into an item the previous one was
            // never measured against.
            val delta = when {
                input.index > previousIndex -> threshold
                input.index < previousIndex -> -threshold
                else -> (input.offset - previousOffset).toFloat()
            }
            previousIndex = input.index
            previousOffset = input.offset
            travel = (if (travel.sign == delta.sign) travel else 0f) + delta
            when {
                input.isPushedAway -> {
                    isSlidAway = true
                    travel = 0f
                }

                input.isAtTop -> {
                    isSlidAway = false
                    travel = 0f
                }

                travel >= threshold -> isSlidAway = true
                travel <= -threshold -> isSlidAway = false
            }
        }
    }
    return isSlidAway
}

/**
 * One reading of everything the button's place is decided from, taken together so that the push being let go of is an
 * occasion to decide again rather than something noticed at the next scroll - which, the list being still by then,
 * may never come.
 */
private data class SlideInput(
    val index: Int,
    val offset: Int,
    val isAtTop: Boolean,
    val isPushedAway: Boolean,
)

/** From this width on the button has room for its label without crowding the list next to it. */
private val EXTENDED_FAB_MIN_WIDTH = 600.dp
private val FAB_MARGIN = 16.dp

/** How far the list has to travel in one direction before the button follows it off the screen or back onto it. */
private val SLIDE_THRESHOLD = 24.dp

/** The bottom padding a list needs so that its last row can scroll out from under the button. */
internal val FAB_CLEARANCE = 88.dp
