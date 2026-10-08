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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

/**
 * How far the app bar of a list screen is filled in (see [SearchableTopAppBar]): not at all while a closed search leaves
 * the pinned header of the list to stand in its place, and completely while the search is open or while the list has
 * no header to stand there. It moves on the spring the search action travels on, since the bar filling in and the
 * list making room for it are part of the search opening.
 *
 * @param isShownWithoutSearch Whether the list has nothing to stand in the bar's place even while the search is closed.
 */
@Composable
internal fun animateAppBarReveal(
    searchState: SearchState,
    isShownWithoutSearch: Boolean,
): State<Float> {
    val isOpen by searchState.isOpen.collectAsStateWithLifecycle()
    return animateFloatAsState(
        targetValue = if (isOpen || isShownWithoutSearch) 1f else 0f,
        animationSpec = searchTravelSpec(),
    )
}

/**
 * The one tonal pill of a list screen's app bar, which is the background of the buttons while the search is closed and
 * the background of the field while it is open, and travels between the two as the search opens and closes rather
 * than one fading out while the other fades in. Behind the buttons, it is what makes them read as the screen's own
 * rather than as the pinned header's: sitting on the header's row next to its name, they would otherwise look like
 * that one section's actions.
 *
 * It is drawn by the bar, and goes from where the buttons' row settles when the search is closed to where the field
 * settles when it is open, on the spring the search action travels on. Both ends are the *lookahead* rectangles, the
 * ones the layout is heading for, each taken from the state it belongs to: the rectangles of the frame itself are
 * still moving (the buttons' row narrowing as the action leaves it, the field's edges sweeping in from beyond the
 * bar's end), and an edge interpolated between two moving ones overshoots and comes back, where between two that stand
 * still it only ever travels the one way. Positions are kept in window coordinates because the three are laid out by
 * different parents, and the bar's own is taken off again as it draws.
 */
internal class SearchPill {

    /** The bar's own coordinates, which the two rectangles are measured from. Read only from placement callbacks. */
    var bar: LayoutCoordinates? = null

    var closedActions by mutableStateOf(Rect.Zero)

    var openField by mutableStateOf(Rect.Zero)
}

/**
 * How far a back gesture that would close the search has taken the field towards closing: the gesture's own progress
 * while it is dragged, animated back to nothing when it is let go of without closing the search. It is held by the app
 * bar rather than by the field because the search action follows it too, and the two only move as one if they read
 * the same value on the same frame.
 */
internal class SearchRecession {

    val progress = Animatable(0f)

    /** The field's full width, which is what the part it loses is a fraction of. */
    var fieldWidth by mutableIntStateOf(0)

    /** How far the field's start edge has moved towards its end, which is how far the search action is moved with it. */
    val startEdgeTravel: Int
        get() = (progress.value * RECEDED_WIDTH_LOSS * fieldWidth).roundToInt()
}

/**
 * The spring the search action travels across the bar on, shared by the room either end of the bar makes for it and
 * by the field whose edge follows it, since the three are one movement and have to arrive together. It is critically
 * damped rather than the theme's spatial spring, which overshoots: a button that travels past the start of the bar
 * and settles back reads as having bounced off the edge of the window.
 */
internal fun <T> searchTravelSpec(visibilityThreshold: T? = null) = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = visibilityThreshold,
)

/** How much of the field's width a back gesture dragged all the way collapses before it is let go of. */
private const val RECEDED_WIDTH_LOSS = 0.25f
