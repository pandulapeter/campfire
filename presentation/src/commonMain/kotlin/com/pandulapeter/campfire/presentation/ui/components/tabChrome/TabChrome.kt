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

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * What carries the parts of a top level screen that stay on screen while two tabs are swapped (the New button, the
 * search pill, and the tools' instrument pill, capo, key, and search, see [TabChromeElement]): the [SharedTransitionScope]
 * laid around the screens, and which kind of transition
 * the navigation specs last decided on ([swap]), since only a swap of two tabs carries them over. Snapshot state, unlike
 * `PredictiveBackMark`, since the parts read it while they are laid out and drawn.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Stable
internal class TabChrome {
    /** Set by the layout around the screens before any of them is composed, which is the only one there is. */
    lateinit var sharedTransitionScope: SharedTransitionScope

    var swap by mutableStateOf(TabSwap.NONE)

    /** Every part composed on a top level screen, for a matched one to read the container color of the other's. */
    val parts = mutableStateListOf<SharedTabChrome>()
}

/** How the screens of the running navigation transition change places, as far as [TabChrome] is concerned. */
internal enum class TabSwap {
    /** Not two tabs: a card dealt or taken off, or nothing animated at all while the launch screen covers the app. */
    NONE,

    /** Two tabs, the one being left fading out before the other fades in. */
    FADE_THROUGH,

    /** Two tabs cross faded along a back gesture. */
    CROSS_FADE,
}

/**
 * The parts of a tab that stay on screen while it is swapped for another, one key each, matched between the two. A part
 * a setting changes on every tab at once (the instrument, the tools' capo) is the same on each, so it stays still where
 * two tabs show it at the same spot.
 */
internal enum class TabChromeElement {
    /**
     * The list screens' New button, and what stands on its spot on the tools: the one button pill on the Chords and the
     * Scales tabs, the Metronome's play button, and the Tuner's reference tone (a row lower on a narrow line).
     */
    NEW_BUTTON,

    /**
     * The list screens' search pill, open or closed, and what stands on its spot on the Tuner tab: the reference pitch's
     * pill (a row lower on a narrow line).
     */
    SEARCH_PILL,

    /** The instrument pill at the top of the Tuner, the Chords, and the Scales tabs. */
    INSTRUMENT_PILL,

    /** The capo beside the instrument pill on the Chords and the Scales tabs. */
    CAPO,

    /** The key before the search on the Chords and the Scales tabs, which is one key shared by the two. */
    TOOL_KEY,

    /** The search field of the Chords and the Scales tabs, whose search mark stays while its hint and query cross. */
    TOOL_SEARCH,
}

/**
 * What a part of a tab holds where it differs from tab to tab (the parts on the New button's spot, and the Tuner's
 * reference pitch on the search pill's, whose pill holds none), so that a matched
 * pair holding the same thing crosses as one and a pair holding different things scales and fades, see [SharedTabChrome].
 */
internal enum class TabChromeContent {
    NEW,
    PREFERENCES,
    PLAY,
    STOP,
    REFERENCE_PITCH,
}

/**
 * One top level screen's share of [TabChrome]: the visibility of its own entry in the navigation transition. There is
 * none outside a top level screen (a card, the screenshot tool's screens drawn alone), where the parts stay put.
 */
@Stable
internal class TabChromeScope(
    val tabChrome: TabChrome,
    val visibilityScope: AnimatedVisibilityScope,
) {
    /** Whether two tabs are being swapped right now, this screen being one of them. */
    val isSwapping: Boolean
        get() = tabChrome.swap != TabSwap.NONE && visibilityScope.transition.let { it.currentState != it.targetState }
}

internal val LocalTabChromeScope = staticCompositionLocalOf<TabChromeScope?> { null }

/** Hands the top level screen [content] is composed for its [TabChromeScope], its entry's visibility read from Navigation 3. */
@Composable
internal fun ProvideTabChromeScope(
    tabChrome: TabChrome,
    content: @Composable () -> Unit,
) {
    val visibilityScope = LocalNavAnimatedContentScope.current
    CompositionLocalProvider(
        LocalTabChromeScope provides remember(tabChrome, visibilityScope) { TabChromeScope(tabChrome, visibilityScope) },
        content = content,
    )
}
