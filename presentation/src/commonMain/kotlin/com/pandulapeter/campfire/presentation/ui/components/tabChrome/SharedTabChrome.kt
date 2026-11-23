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

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot

/**
 * One part of a top level screen that stays on screen while two tabs are swapped (see [TabChromeElement]), or nothing
 * outside a top level screen. While two tabs swap, both screens' copies of it are drawn over both screens
 * ([sharedTabChrome]), so the screens' fade through passes it by:
 *
 * - Where the other screen has the same part, the two are one surface at one rectangle ([travellingBounds]), which
 *   goes from the rectangle of the copy being left to that of the one being opened as the latter's presence does, on
 *   the theme's spatial spring. Both copies work it out from the same two rectangles and the same progress,
 *   so they never part. Only the copy being left draws the container there, its color going from its own to the other
 *   part's on the same progress, so a filled New button turns into the tonal Preferences pill rather than one fading
 *   out under the other. What each copy holds rides the rectangle, clipped to it, and crosses with the other's
 *   ([matchedContentAlpha]), scaling as it does where the two hold different things ([matchedContentScale]), so a
 *   New button turning into a play button reads as its icon changing. The copy being left draws the container because it is already drawn over both screens on
 *   the swap's first frame, while the one being opened, attached on that frame, is drawn there only from the next.
 * - Where it has none (Settings; a list screen with its search open, a selection, or read only mode), the part scales
 *   and fades on its own, out of the screen being left or into the one being opened. Left inside its screen, which is
 *   gone in 90 ms, it would blink rather than scale.
 *
 * The rectangles are the parts' own, as laid out, in the root's coordinates, which a tab swap moves neither screen in.
 * They are read while drawing, so nothing is composed or laid out again on the swap's frames.
 *
 * Neither happens outside a swap of two tabs: under a card being dealt or taken off, the part moves with its screen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Stable
internal class SharedTabChrome(
    private val scope: TabChromeScope,
    private val element: TabChromeElement,
    private val presence: State<Float>,
    private val fade: State<Float>,
    private val screenAlpha: State<Float>,
    containerColor: Color,
    content: TabChromeContent?,
) {
    val sharedTransitionScope get() = scope.tabChrome.sharedTransitionScope

    /** The color of the part's own container, which the other screen's copy sets out from while the two are matched. */
    var containerColor by mutableStateOf(containerColor)

    /** What the part holds, see [rememberSharedTabChrome]. */
    var content by mutableStateOf(content)

    /** The part's own rectangle in its node's coordinates, see [rememberSharedTabChrome]. */
    var localBounds: ((LayoutCoordinates) -> Rect)? = null

    /** The node that the part's rectangle is measured in, written as it is placed. */
    var coordinates: LayoutCoordinates? = null

    /** Whether the tabs are being swapped right now, which is when the part is drawn over both screens. */
    val isSwapping get() = scope.isSwapping

    /** The same part on the other screen of the swap, the one this shares its rectangle with, or null for none. */
    val counterpart: SharedTabChrome?
        get() = if (!isSwapping || boundsInRoot() == null) null else counterpartAmong(
            part = this,
            parts = scope.tabChrome.parts.filter { it.element == element && it.isSwapping && it.boundsInRoot() != null },
            isEntering = { it.isEntering },
        )

    /** Whether the part shares its rectangle with the other screen's right now. */
    val isMatched get() = counterpart != null

    /** Whether the part is drawn alone, scaling and fading, rather than sharing its rectangle. */
    val isAlone get() = isSwapping && !isMatched

    /** Whether the screen holding the part is the one being opened. */
    val isEntering get() = scope.visibilityScope.transition.targetState == EnterExitState.Visible

    /** How large the part is drawn while it is [isAlone], around its middle; whole otherwise. */
    val aloneScale get() = if (isAlone) TAB_CHROME_HIDDEN_SCALE + (1f - TAB_CHROME_HIDDEN_SCALE) * presence.value else 1f

    /** How opaque the part is while it is [isAlone], on the effects spring, so it is gone before the spatial one settles. */
    val aloneAlpha get() = fade.value.coerceIn(0f, 1f)

    /**
     * How opaque what is drawn over the part but belongs to its screen, and stands off the part (see
     * [tabChromeForeground]), has to be to fade as its screen fades: with the screen, or with the part while that is alone.
     */
    val foregroundAlpha get() = if (isAlone) aloneAlpha else screenAlpha.value

    /** Whether the part and its counterpart hold the same thing (New on either list), which then never changes. */
    private val isContentShared get() = counterpart?.content == content

    /**
     * How opaque what stands on the part is while it is [isMatched]. Where both copies hold the same, the copy being
     * opened, drawn over the one being left, comes in whole during the first half of the crossfade, and the one being
     * left only goes during the second, so it never dims; where they differ, the two cross as [crossfadeAlpha] does.
     */
    val matchedContentAlpha get() = if (isContentShared) (fade.value * 2f).coerceIn(0f, 1f) else crossfadeAlpha

    /**
     * How large what stands on the part is drawn while it is [isMatched], around the travelling rectangle's middle:
     * whole where both copies hold the same, and otherwise growing in on the copy being opened and shrinking away on
     * the one being left, on the spatial spring, as a part on its own does ([aloneScale]).
     */
    val matchedContentScale get() = if (isContentShared) 1f else TAB_CHROME_HIDDEN_SCALE + (1f - TAB_CHROME_HIDDEN_SCALE) * presence.value

    /**
     * How opaque what rides the part is while it is [isMatched] and the two copies hold different things (an open field
     * and a closed pill's buttons, which [matchedContentAlpha] would show whole on top of each other halfway): a plain
     * crossfade, the one going as the other comes.
     */
    val crossfadeAlpha get() = aloneAlpha

    /** The part's own rectangle in the root's coordinates, or null while it has none (not placed, or empty). */
    fun boundsInRoot(): Rect? {
        val node = coordinates?.takeIf { it.isAttached } ?: return null
        val bounds = localBounds?.invoke(node) ?: Rect(0f, 0f, node.size.width.toFloat(), node.size.height.toFloat())
        return bounds.takeUnless { it.isEmpty }?.translate(node.positionInRoot())
    }

    /**
     * The rectangle the part and its counterpart share on this frame, in the coordinates of the node the part is measured
     * in ([coordinates]), or null while it is not matched.
     */
    fun travellingBounds(): Rect? {
        val other = counterpart ?: return null
        val own = boundsInRoot() ?: return null
        val others = other.boundsInRoot() ?: return null
        val node = coordinates ?: return null
        val travelling = if (isEntering) lerp(others, own, presence.value) else lerp(own, others, other.presence.value)
        return travelling.translate(-node.positionInRoot())
    }

    /**
     * The color the part's container is drawn in on this frame, or null for none: the copy being opened draws none while
     * the two are matched, and the copy being left heads for the other's color, see [SharedTabChrome].
     */
    val currentContainerColor: Color?
        get() {
            val other = counterpart ?: return containerColor
            return if (isEntering) null else lerp(containerColor, other.containerColor, other.presence.value.coerceIn(0f, 1f))
        }
}

/**
 * The [SharedTabChrome] of [element] on the top level screen this is composed in, or null outside one.
 *
 * @param containerColor The color the part's container is drawn in, which a matched part on the other screen turns
 *   into or sets out from.
 * @param content What the part holds, which crosses with a counterpart's as one where the two are the same, and
 *   scaling and fading where they differ; null for a part that holds the same on every tab it is on.
 * @param localBounds The part's rectangle in the coordinates of the node it is drawn by, read while drawing; the whole
 *   node when null. The search pill is a stretch of its bar, measured from its neighbors' placement, and has none (an
 *   empty one) on its first frame, so it shares nothing until it has.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberSharedTabChrome(
    element: TabChromeElement,
    containerColor: Color,
    content: TabChromeContent? = null,
    localBounds: ((LayoutCoordinates) -> Rect)? = null,
): SharedTabChrome? {
    val scope = LocalTabChromeScope.current ?: return null
    val motionScheme = MaterialTheme.motionScheme
    val transition = scope.visibilityScope.transition
    val presence = transition.animateSwap(scope) { motionScheme.defaultSpatialSpec() }
    val fade = transition.animateSwap(scope) { motionScheme.defaultEffectsSpec() }
    val screenAlpha = transition.animateSwap(scope) {
        if (targetState == EnterExitState.Visible) tabFadeInSpec() else tabFadeOutSpec()
    }
    val chrome = remember(scope, element) {
        SharedTabChrome(
            scope = scope,
            element = element,
            presence = presence,
            fade = fade,
            screenAlpha = screenAlpha,
            containerColor = containerColor,
            content = content,
        )
    }
    SideEffect {
        chrome.containerColor = containerColor
        chrome.content = content
        chrome.localBounds = localBounds
    }
    DisposableEffect(chrome) {
        scope.tabChrome.parts.add(chrome)
        onDispose { scope.tabChrome.parts.remove(chrome) }
    }
    return chrome
}

/**
 * How far the screen this transition shows is in, 0 to 1: on [fadeThroughSpec] while two tabs fade through, snapped outside
 * a swap of two tabs, so that a part animating there never keeps a card's transition running, and linear along a back
 * gesture, which seeks the transition: a spring would run ahead of the finger and the screens.
 */
@Composable
private fun Transition<EnterExitState>.animateSwap(
    scope: TabChromeScope,
    fadeThroughSpec: Transition.Segment<EnterExitState>.() -> FiniteAnimationSpec<Float>,
): State<Float> = animateFloat(
    transitionSpec = {
        when (scope.tabChrome.swap) {
            TabSwap.NONE -> snap()
            TabSwap.FADE_THROUGH -> fadeThroughSpec()
            TabSwap.CROSS_FADE -> predictiveBackSpec()
        }
    },
) { if (it == EnterExitState.Visible) 1f else 0f }

/** How small a part is as it scales out of the screen being left or into the one being opened, see [SharedTabChrome]. */
internal const val TAB_CHROME_HIDDEN_SCALE = 0.6f

/**
 * Which of [parts] (the copies of one part being swapped, [part] among them) [part] shares its rectangle with: a copy
 * being opened with the first being left, and a copy being left with the one being opened only where that picks it in
 * turn. A third tab opened during a swap leaves two copies on their way out, which must neither match each other nor
 * both draw a container on the way to the one being opened; the one not picked goes alone.
 */
internal fun <T : Any> counterpartAmong(part: T, parts: List<T>, isEntering: (T) -> Boolean): T? {
    val firstOpposite = parts.firstOrNull { it !== part && isEntering(it) != isEntering(part) } ?: return null
    return if (isEntering(part)) firstOpposite else firstOpposite.takeIf { parts.firstOrNull { !isEntering(it) } === part }
}
