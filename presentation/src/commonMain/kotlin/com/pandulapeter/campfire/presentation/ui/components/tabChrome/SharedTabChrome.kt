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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp

/**
 * One part of a top level screen that stays on screen while two tabs are swapped (see [TabChromeElement]), or nothing
 * outside a top level screen. Its [sharedTabChrome] node is drawn over both screens for as long as they swap, so the
 * screens' fade through passes it by:
 *
 * - Where the other screen has the same part, the two share one rectangle that travels between theirs on the theme's
 *   spatial spring (`sharedBounds`), and it is one surface: only the copy of the screen being opened draws the
 *   container, its color going from the other part's to its own on the same spring, so a filled New button turns into
 *   the tonal Preferences pill rather than one fading out under the other. What each copy holds (its icons) is clipped
 *   to the travelling shape and crosses with the other's ([SharedTabChrome.matchedContentAlpha]). The copy being
 *   opened is the one that draws the container because it is the one that stays once the swap is over.
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
    private val element: TabChromeElement,
    val sharedContentState: SharedTransitionScope.SharedContentState,
    private val presence: State<Float>,
    private val screenAlpha: State<Float>,
    private val crossfade: State<Float>,
    private val motionScheme: MotionScheme,
    containerColor: Color,
) {
    val sharedTransitionScope get() = scope.tabChrome.sharedTransitionScope

    val visibilityScope get() = scope.visibilityScope

    /** The color of the part's own container, which the other screen's copy sets out from while the two are matched. */
    var containerColor by mutableStateOf(containerColor)

    /** Whether the tabs are being swapped right now, which is when the part is drawn over both screens. */
    val isSwapping get() = scope.isSwapping

    /** Whether the part is drawn alone, scaling and fading by [presence], rather than sharing its rectangle. */
    val isAlone get() = scope.isSwapping && !sharedContentState.isMatchFound

    /** Whether the part shares its rectangle with the other screen's right now. */
    val isMatched get() = scope.isSwapping && sharedContentState.isMatchFound

    /** How far the part is in while it is [isAlone]: 0 out of sight, 1 whole, past 1 on the spring's overshoot. */
    val presenceProgress get() = presence.value

    /** How large the part is drawn while it is [isAlone], around its middle; whole otherwise. */
    val aloneScale get() = if (isAlone) TAB_CHROME_HIDDEN_SCALE + (1f - TAB_CHROME_HIDDEN_SCALE) * presence.value else 1f

    /**
     * How opaque what is drawn over the part but belongs to its screen, and stands off the part (see
     * [tabChromeForeground]), has to be to fade as its screen fades: with the screen, or with the part while that is alone.
     */
    val foregroundAlpha get() = if (isAlone) presence.value.coerceIn(0f, 1f) else screenAlpha.value

    /**
     * How opaque what stands on the part is while it is [isMatched]: the copy being opened, drawn under the one being
     * left, comes in whole during the first half of the crossfade, and the one being left only goes during the second,
     * so an icon both tabs have (New on either list, the search mark) never dims, and two different ones still cross.
     */
    val matchedContentAlpha get() = (crossfade.value * 2f).coerceIn(0f, 1f)

    /** Whether the screen holding the part is the one being opened. */
    val isEntering get() = scope.visibilityScope.transition.targetState == EnterExitState.Visible

    /**
     * The color the part's container is drawn in on this frame, or null for none: the copy being left draws none while
     * the two are matched, and the copy being opened sets out from the other's color, see [SharedTabChrome].
     */
    val currentContainerColor: Color?
        get() = when {
            !isMatched -> containerColor
            !isEntering -> null
            else -> lerp(
                start = counterpart?.containerColor ?: containerColor,
                stop = containerColor,
                fraction = presence.value.coerceIn(0f, 1f),
            )
        }

    /** The same part on the other screen of the swap, the one this shares its rectangle with. */
    private val counterpart: SharedTabChrome?
        get() = scope.tabChrome.parts.firstOrNull { it !== this && it.element == element && it.isSwapping }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val boundsTransform = BoundsTransform { _, _ ->
        when (val spec = motionScheme.defaultSpatialSpec<Rect>()) {
            is SpringSpec -> spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness, visibilityThreshold = Rect.VisibilityThreshold)
            else -> spec
        }
    }
}

/**
 * The [SharedTabChrome] of [element] on the top level screen this is composed in, or null outside one.
 *
 * @param containerColor The color the part's container is drawn in, which a matched part on the other screen turns
 *   into or sets out from.
 * @param isReady Whether the part knows its rectangle yet. One measured from its neighbors' placement (the search pill)
 *   has none on its first frame, and shares nothing until it has, rather than sending the other one towards the corner.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberSharedTabChrome(
    element: TabChromeElement,
    containerColor: Color,
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
    val crossfade = transition.animateFloat(
        transitionSpec = {
            when (scope.tabChrome.swap) {
                TabSwap.NONE -> snap()
                TabSwap.FADE_THROUGH -> motionScheme.defaultEffectsSpec()
                TabSwap.CROSS_FADE -> predictiveBackSpec()
            }
        },
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val chrome = remember(scope, sharedContentState, motionScheme) {
        SharedTabChrome(
            scope = scope,
            element = element,
            sharedContentState = sharedContentState,
            presence = presence,
            screenAlpha = screenAlpha,
            crossfade = crossfade,
            motionScheme = motionScheme,
            containerColor = containerColor,
        )
    }
    SideEffect { chrome.containerColor = containerColor }
    DisposableEffect(chrome) {
        scope.tabChrome.parts.add(chrome)
        onDispose { scope.tabChrome.parts.remove(chrome) }
    }
    return chrome
}

/**
 * Makes this node the part [chrome] stands for, see [SharedTabChrome]. The node's own rectangle is the one shared, and
 * what is drawn in it is measured to the rectangle as that travels, so a shape drawn to its size (a stadium of its height)
 * changes shape rather than being stretched. The container is the stadium of the node's height in [SharedTabChrome]'s
 * color, drawn behind what the node holds. A no-op without [chrome].
 *
 * @param zIndex Where it is drawn among the other parts over the screens: the search pill under the bar's content under
 *   the New button.
 * @param isContainerDrawnOutsideSwaps Whether the node draws its container whenever it is shown, or only while the tabs
 *   swap, its screen drawing it otherwise (under the presses of a pill's buttons, which this node holds).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun Modifier.sharedTabChrome(
    chrome: SharedTabChrome?,
    zIndex: Float,
    isContainerDrawnOutsideSwaps: Boolean,
): Modifier = if (chrome == null) this else with(chrome.sharedTransitionScope) {
    this@sharedTabChrome
        .renderInSharedTransitionScopeOverlay(zIndexInOverlay = zIndex, renderInOverlay = { chrome.isAlone })
        .graphicsLayer {
            if (chrome.isAlone) {
                val scale = chrome.aloneScale
                scaleX = scale
                scaleY = scale
                alpha = chrome.presenceProgress.coerceIn(0f, 1f)
            }
        }
        .sharedBounds(
            sharedContentState = chrome.sharedContentState,
            animatedVisibilityScope = chrome.visibilityScope,
            // Neither copy fades as a whole: the container is one, and what each holds fades below.
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = chrome.boundsTransform,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            // The copy being left over the one being opened, whose container is the one drawn, so that the icon fading
            // out stays on top of it rather than under it.
            zIndexInOverlay = zIndex + if (chrome.isEntering) 0f else LEAVING_Z_INDEX_OFFSET,
        )
        .drawBehind {
            if (isContainerDrawnOutsideSwaps || chrome.isSwapping) {
                chrome.currentContainerColor?.let { drawRoundRect(color = it, cornerRadius = CornerRadius(size.height / 2)) }
            }
        }
        .graphicsLayer {
            // Only while the tabs swap: a pill's buttons reach past its top and bottom, and a clip would cut their touch.
            if (chrome.isMatched) {
                alpha = chrome.matchedContentAlpha
                clip = true
                shape = CircleShape
            }
        }
}

/** How small a part is as it scales out of the screen being left or into the one being opened, see [SharedTabChrome]. */
private const val TAB_CHROME_HIDDEN_SCALE = 0.6f

/** What puts the screen being left's copy of a part over the one being opened's, both drawn in the same rectangle. */
private const val LEAVING_Z_INDEX_OFFSET = 0.1f

/** The order the parts are drawn in over both screens, see [sharedTabChrome]. */
internal const val SEARCH_PILL_Z_INDEX = 1f
internal const val SEARCH_PILL_CONTENT_Z_INDEX = 2f
internal const val NEW_BUTTON_Z_INDEX = 3f
