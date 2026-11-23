/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.tabChrome

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * One part of a top level screen that stays on screen while two tabs are swapped (see [TabChromeElement]), or nothing
 * outside a top level screen. Its [sharedTabChrome] node is drawn over both screens for as long as they swap, so the
 * screens' fade through passes it by:
 *
 * - Where the other screen has the same part, the two share one rectangle that travels between theirs on the theme's
 *   spatial spring (`sharedBounds`), the screen being opened's drawn over the one being left and fading in over it, so
 *   the two read as one shape changing color and size, never as anything see-through.
 * - Where it has none (Settings, the Metronome, the Tuner; a list screen with its search open, a selection, or read
 *   only mode), the part is drawn over both screens all the same and scales and fades on its own, on the same spring,
 *   out of the screen being left or into the one being opened. Left inside its screen, which is gone in 90 ms, it
 *   would blink rather than scale.
 *
 * Neither happens outside a swap of two tabs: under a card being dealt or taken off, the part moves with its screen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Stable
internal class SharedTabChrome(
    private val scope: TabChromeScope,
    val sharedContentState: SharedTransitionScope.SharedContentState,
    private val presence: State<Float>,
    private val screenAlpha: State<Float>,
    private val motionScheme: MotionScheme,
) {
    val sharedTransitionScope get() = scope.tabChrome.sharedTransitionScope

    val visibilityScope get() = scope.visibilityScope

    /** Whether the tabs are being swapped right now, which is when the part is drawn over both screens. */
    val isSwapping get() = scope.isSwapping

    /** Whether the part is drawn alone, scaling and fading by [presence], rather than sharing its rectangle. */
    val isAlone get() = scope.isSwapping && !sharedContentState.isMatchFound

    /** How far the part is in while it is [isAlone]: 0 out of sight, 1 whole, past 1 on the spring's overshoot. */
    val presenceProgress get() = presence.value

    /**
     * How opaque what is drawn over the part, but belongs to its screen, has to be to fade as its screen fades (see
     * [tabChromeForeground]): with the screen, or with the part while that is alone.
     */
    val foregroundAlpha get() = if (isAlone) presence.value.coerceIn(0f, 1f) else screenAlpha.value

    /** Whether the screen holding the part is the one being opened, which is drawn over the one being left. */
    val isEntering get() = scope.visibilityScope.transition.targetState == EnterExitState.Visible

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val boundsTransform = BoundsTransform { _, _ ->
        when (val spec = motionScheme.defaultSpatialSpec<Rect>()) {
            is SpringSpec -> spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness, visibilityThreshold = Rect.VisibilityThreshold)
            else -> spec
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val fadeInSpec get() = motionScheme.defaultEffectsSpec<Float>()
}

/**
 * The [SharedTabChrome] of [element] on the top level screen this is composed in, or null outside one.
 *
 * @param isReady Whether the part knows its rectangle yet. One measured from its neighbors' placement (the search pill)
 *   has none on its first frame, and shares nothing until it has, rather than sending the other one towards the corner.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberSharedTabChrome(
    element: TabChromeElement,
    isReady: () -> Boolean = { true },
): SharedTabChrome? {
    val scope = LocalTabChromeScope.current ?: return null
    val currentIsReady by rememberUpdatedState(isReady)
    val config = remember {
        object : SharedTransitionScope.SharedContentConfig {
            override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
                get() = currentIsReady()
        }
    }
    val sharedContentState = with(scope.tabChrome.sharedTransitionScope) { rememberSharedContentState(element, config) }
    val motionScheme = MaterialTheme.motionScheme
    val transition = scope.visibilityScope.transition
    // Snapped outside a swap of two tabs, so that a part animating there never keeps a card's transition running.
    val presence = transition.animateFloat(
        transitionSpec = { if (scope.tabChrome.swap == TabSwap.NONE) snap() else motionScheme.defaultSpatialSpec() },
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val screenAlpha = transition.animateFloat(
        transitionSpec = {
            when (scope.tabChrome.swap) {
                TabSwap.NONE -> snap()
                TabSwap.FADE_THROUGH -> if (targetState == EnterExitState.Visible) tabFadeInSpec() else tabFadeOutSpec()
                TabSwap.CROSS_FADE -> predictiveBackSpec()
            }
        },
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return remember(scope, sharedContentState, motionScheme) {
        SharedTabChrome(
            scope = scope,
            sharedContentState = sharedContentState,
            presence = presence,
            screenAlpha = screenAlpha,
            motionScheme = motionScheme,
        )
    }
}

/**
 * Makes this node the part [chrome] stands for, see [SharedTabChrome]. The node's own rectangle is the one shared, and
 * what is drawn in it is measured to the rectangle as that travels, so a shape drawn to its size (a stadium of its height)
 * changes shape rather than being stretched. A no-op without [chrome].
 *
 * @param zIndex Where it is drawn among the other parts over the screens: the search pill under the bar's content under
 *   the New button.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun Modifier.sharedTabChrome(
    chrome: SharedTabChrome?,
    zIndex: Float,
): Modifier = if (chrome == null) this else with(chrome.sharedTransitionScope) {
    this@sharedTabChrome
        .renderInSharedTransitionScopeOverlay(zIndexInOverlay = zIndex, renderInOverlay = { chrome.isAlone })
        .graphicsLayer {
            if (chrome.isAlone) {
                val progress = chrome.presenceProgress
                val scale = TAB_CHROME_HIDDEN_SCALE + (1f - TAB_CHROME_HIDDEN_SCALE) * progress
                scaleX = scale
                scaleY = scale
                alpha = progress.coerceIn(0f, 1f)
            }
        }
        .sharedBounds(
            sharedContentState = chrome.sharedContentState,
            animatedVisibilityScope = chrome.visibilityScope,
            // The one being left stays opaque under the one fading in, which covers it whole by the end.
            enter = fadeIn(chrome.fadeInSpec),
            exit = ExitTransition.None,
            boundsTransform = chrome.boundsTransform,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            zIndexInOverlay = zIndex + if (chrome.isEntering) ENTERING_Z_INDEX_OFFSET else 0f,
        )
}

/**
 * Draws this node over both screens while their tabs are swapped, fading as its screen does: what is drawn on top of a
 * part of [chrome] but belongs to the screen (the search pill's buttons and field), which the part would otherwise
 * cover. Placed where it is laid out, never moved, so nothing on it travels with the part. A no-op without [chrome].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun Modifier.tabChromeForeground(
    chrome: SharedTabChrome?,
    zIndex: Float,
): Modifier = if (chrome == null) this else with(chrome.sharedTransitionScope) {
    this@tabChromeForeground
        .renderInSharedTransitionScopeOverlay(zIndexInOverlay = zIndex, renderInOverlay = { chrome.isSwapping })
        .graphicsLayer {
            if (chrome.isSwapping) {
                alpha = chrome.foregroundAlpha
            }
        }
}

/** How small a part is as it scales out of the screen being left or into the one being opened, see [SharedTabChrome]. */
private const val TAB_CHROME_HIDDEN_SCALE = 0.6f

/** What puts the screen being opened's copy of a part over the one being left's, both drawn in the same rectangle. */
private const val ENTERING_Z_INDEX_OFFSET = 0.1f

/** The order the parts are drawn in over both screens, see [sharedTabChrome]. */
internal const val SEARCH_PILL_Z_INDEX = 1f
internal const val SEARCH_PILL_CONTENT_Z_INDEX = 2f
internal const val NEW_BUTTON_Z_INDEX = 3f
