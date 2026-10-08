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

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import kotlin.math.roundToInt

/**
 * Lays the navigation chrome out and hands [content] the size of the window along with what the chrome takes out of
 * it, all in one pass.
 *
 * The thickness has to be *measured*: Material keeps the size of the rail and of the bar - and of the insets they
 * cover - to itself. Reporting it back as state from the laid out chrome would report it one layout pass too late,
 * so the app's very first frame would be composed as if the window had no chrome in it at all: the screens would
 * cover the rail and settle their column counts for the full width, only to be laid out again a frame later. That
 * frame is not a fleeting one either - it is the first frame of a cold start, and the second one is several
 * hundred milliseconds behind it while everything the app draws with is still being loaded.
 *
 * Subcomposing the chrome ahead of the content is what makes its size available to the composition that needs it.
 * It costs no layer that was not there already, since the window size the screens are laid out for used to come
 * through a `BoxWithConstraints`, which is a [SubcomposeLayout] of exactly this kind.
 *
 * The chrome is placed *under* [content]: the song details screen is dealt over the whole window and covers it,
 * rather than the two of them animating side by side. While the top level screens draw a chrome of their own
 * ([isChromePlaced] off), this one is still measured, for its thickness, but not placed, so it is neither drawn nor
 * touched; it stays composed, so the state of its items carries on once it is placed again.
 *
 * A window that crosses from one kind of chrome to another (see [NavigationChromeKind]) does not switch between them
 * in one frame: the chrome it is leaving fades out towards its edge while the one it is getting fades in from its own,
 * and the room they take out of the window is worked out between the two on the same spring, so the screens next to
 * them travel rather than jump. The chrome is still measured for the kind the window has *now*, in the frame the
 * window changes, so what the screens settle at is known at once ([NavigationChromeSize.settledRailWidth]) while what
 * they are inset by catches up.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NavigationChromeScaffold(
    modifier: Modifier = Modifier,
    isChromePlaced: Boolean,
    chrome: @Composable (kind: NavigationChromeKind) -> Unit,
    content: @Composable (windowWidth: Dp, windowSize: WindowSize, kind: NavigationChromeKind, size: NavigationChromeSize) -> Unit,
) {
    val transition = remember { NavigationChromeTransition() }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(transition.generation) {
        if (transition.generation > 0) {
            animate(initialValue = 0f, targetValue = 1f, animationSpec = spec) { value, _ -> transition.progress = value }
            transition.outgoingKind = null
        }
    }
    SubcomposeLayout(modifier) { constraints ->
        val windowWidth = constraints.maxWidth.toDp()
        val windowSize = WindowSize.fromWidth(windowWidth)
        val kind = navigationChromeKind(windowWidth)
        // Loose constraints, so that the rail and the bar each take only the one dimension they want.
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val chromePlaceable = subcompose(ChromeSlot.CHROME) { chrome(kind) }.single().measure(looseConstraints)
        transition.update(kind = kind, railWidth = if (kind.isRail) chromePlaceable.width else 0, barHeight = if (kind.isRail) 0 else chromePlaceable.height)
        val outgoingKind = transition.outgoingKind
        val outgoingPlaceable = outgoingKind?.let { subcompose(ChromeSlot.OUTGOING_CHROME) { chrome(it) }.single().measure(looseConstraints) }
        val progress = transition.progress
        val chromeSize = NavigationChromeSize(
            railWidth = lerp(transition.fromRailWidth, transition.toRailWidth, progress).toDp(),
            barHeight = lerp(transition.fromBarHeight, transition.toBarHeight, progress).toDp(),
            settledRailWidth = transition.toRailWidth.toDp(),
        )
        val contentPlaceable = subcompose(ChromeSlot.CONTENT) { content(windowWidth, windowSize, kind, chromeSize) }
            .single()
            .measure(constraints)
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Placed relatively, so that the rail sits on the start edge the screens are inset from rather than always
            // on the left one.
            if (isChromePlaced) {
                if (outgoingPlaceable != null) {
                    placeChrome(outgoingPlaceable, outgoingKind, otherKind = kind, visibility = 1f - progress, windowHeight = constraints.maxHeight)
                }
                placeChrome(
                    chromePlaceable,
                    kind,
                    otherKind = outgoingKind,
                    visibility = if (outgoingKind == null) 1f else progress,
                    windowHeight = constraints.maxHeight
                )
            }
            contentPlaceable.placeRelative(x = 0, y = 0)
        }
    }
}

/**
 * Puts one of the two chromes of [NavigationChromeScaffold] on its edge, [visibility] of the way faded in. A chrome
 * that is handing over to or from one on the other edge also slides in from that edge as it fades - a bar and a rail
 * come from different directions - while two rails share an edge and only fade into each other.
 */
private fun Placeable.PlacementScope.placeChrome(
    placeable: Placeable,
    kind: NavigationChromeKind,
    otherKind: NavigationChromeKind?,
    visibility: Float,
    windowHeight: Int,
) {
    val slide = if (otherKind != null && otherKind.isRail != kind.isRail) 1f - visibility else 0f
    if (kind.isRail) {
        placeable.placeRelativeWithLayer(x = -(placeable.width * slide).roundToInt(), y = 0) { alpha = visibility }
    } else {
        placeable.placeRelativeWithLayer(x = 0, y = windowHeight - placeable.height + (placeable.height * slide).roundToInt()) { alpha = visibility }
    }
}

private enum class ChromeSlot { CHROME, OUTGOING_CHROME, CONTENT }

/**
 * Where [NavigationChromeScaffold] is in handing the window from one [NavigationChromeKind] to another. Plain fields
 * where only the layout reads them, state where the composition does (the running number that restarts the animation)
 * or where a change has to lay the scaffold out again (the progress, the chrome on its way out): the scaffold writes
 * to it from its measure block, since that is where the window's width first becomes known.
 *
 * The sizes are in pixels, the chrome being measured there, and [progress] is how far the room the chrome takes has
 * come from the `from` sizes, what was on screen when the kind changed, to the `to` ones.
 */
private class NavigationChromeTransition {
    private var kind: NavigationChromeKind? = null
    var fromRailWidth = 0f
        private set
    var fromBarHeight = 0f
        private set
    var toRailWidth = 0f
        private set
    var toBarHeight = 0f
        private set
    var progress by mutableFloatStateOf(1f)
    var outgoingKind by mutableStateOf<NavigationChromeKind?>(null)
    var generation by mutableIntStateOf(0)
        private set

    /** Takes the chrome measured for the window on this frame; a change of [kind] starts the handover. */
    fun update(kind: NavigationChromeKind, railWidth: Int, barHeight: Int) {
        val previousKind = this.kind
        if (previousKind != null && previousKind != kind) {
            // From what is on screen, which an interrupted handover has not finished getting to.
            fromRailWidth = lerp(fromRailWidth, toRailWidth, progress)
            fromBarHeight = lerp(fromBarHeight, toBarHeight, progress)
            progress = 0f
            outgoingKind = previousKind
            generation++
        }
        this.kind = kind
        toRailWidth = railWidth.toFloat()
        toBarHeight = barHeight.toFloat()
        if (previousKind == null) {
            fromRailWidth = toRailWidth
            fromBarHeight = toBarHeight
        }
    }
}
