/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/** How the deck is changing in the transition that is running, which decides which of its two screens is darkened. */
internal enum class DeckMotion {
    /** A tab swap, or nothing animated at all: neither screen is under the other, so neither is darkened. */
    None,

    /** A card is dealt over the screen being left, which darkens as it is covered. */
    Push,

    /** The top card is taken off the screen being returned to, which starts darkened and clears as it is uncovered. */
    Pop,

    /** [Pop], following the back gesture rather than a spring. */
    PredictivePop,
}

/**
 * What the transition specs tell every entry's scrim about the transition they have just decided on. The specs are
 * evaluated in the composition of each screen of a transition before that screen's own content, so the screen reads
 * the motion of the transition it is part of, an interrupted one included.
 */
@Stable
internal class NavigationScrim {
    var motion by mutableStateOf(DeckMotion.None)

    /**
     * Whether the transition is following a back gesture, a tab swap's included. Read when the gesture completes, which
     * is when the specs have last been asked about it, and plain rather than snapshot state, since nothing draws it.
     */
    var isPredictiveBack = false
}

/**
 * How much of a dialog's scrim the screen this is called from is under, from 0 to 1. Only the screen underneath a
 * moving card is ever darkened: fully once a card has been dealt over it, and from fully to not at all as the card is
 * taken off it, in step with the card's slide - the same spring, or the back gesture's linear progress, which the
 * transition seeks this along with the slides. The card on top, and either screen of a tab swap, stay clear.
 *
 * Animated on the screen's own enter and exit transition, so it settles exactly when the screen does and is read
 * only while drawing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberScrimCoverage(scrim: NavigationScrim): State<Float> {
    val slideSpec = MaterialTheme.motionScheme.slideFractionSpec()
    return LocalNavAnimatedContentScope.current.transition.animateFloat(
        transitionSpec = { if (scrim.motion == DeckMotion.PredictivePop) tween(PREDICTIVE_BACK_DURATION, easing = LinearEasing) else slideSpec },
        label = "navigationScrim",
    ) { state ->
        when (state) {
            EnterExitState.PreEnter -> if (scrim.motion == DeckMotion.Pop || scrim.motion == DeckMotion.PredictivePop) 1f else 0f
            EnterExitState.Visible -> 0f
            EnterExitState.PostExit -> if (scrim.motion == DeckMotion.Push) 1f else 0f
        }
    }
}

/**
 * Draws a dialog's scrim over everything this draws, as dark as [coverage] says it is covered. Clamped, since the
 * spring it follows can overshoot either end.
 */
internal fun Modifier.coveredScreenScrim(color: Color, coverage: () -> Float) = drawWithContent {
    drawContent()
    val alpha = coverage().coerceIn(0f, 1f) * COVERED_SCREEN_SCRIM_ALPHA
    if (alpha > 0f) {
        drawRect(color = color, alpha = alpha)
    }
}

/** Material's scrim behind a dialog or a modal sheet, which is what a fully covered screen is darkened to. */
private const val COVERED_SCREEN_SCRIM_ALPHA = 0.32f
