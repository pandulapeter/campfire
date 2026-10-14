/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.areDrawablesLoaded
import com.pandulapeter.campfire.presentation.ui.platform.isLaunchScreenWholeStartup
import com.pandulapeter.campfire.presentation.ui.platform.isStartupScreenHeldUntilAppReady
import com.pandulapeter.campfire.presentation.ui.theme.LaunchScreenColors

/**
 * [LaunchScreen] over the app until there is something to look at underneath, and the moment the shell is told the
 * app is ready, see `CampfireApp`'s `onAppReady`. Drawn last, over the app.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LaunchScreenGate(
    viewModel: CampfireViewModel,
    arePreferencesLoaded: Boolean,
    isThemeSettled: Boolean,
    launchScreenColors: LaunchScreenColors,
    onAppReady: () -> Unit,
) {
    val hasLibraryToShow by viewModel.launchState.hasLibraryToShow.collectAsStateWithLifecycle()
    // The launch screen covers the app rather than standing in for it, and it fades away once there is
    // something to look at underneath: the app composes, lays out and draws behind it in the meantime, so
    // holding it through the first read of the library costs none of the time that read was going to take
    // anyway. What it covers is the handful of frames the song list would otherwise open on its loading
    // indicator for, and a startup screen handing over to a spinner is two startup screens in a row - the
    // very thing onAppReady exists to keep the other shells from doing.
    //
    // The colors have to have stopped moving as well, or the launch screen would be taken away halfway
    // through its own cross fade from the theme the window opened on to the stored one (the app underneath
    // snaps to it at once, see CampfireTheme).
    //
    // Read once: the composition that took the launch screen away is not always the one drawing the app
    // (Android recreates its activity on the configuration changes it does not handle itself, see the
    // manifest, and when the system reclaims it), and one that starts after it has nothing to cover.
    val hasShownAppBefore = remember { viewModel.hasShownApp }
    var isAppReady by remember { mutableStateOf(hasShownAppBefore) }
    if (hasShownAppBefore) {
        // The shell still holds a startup screen of its own until it is told, the Android activity's pre-draw
        // gate.
        LaunchedEffect(Unit) { onAppReady() }
    } else if (isAppReady && isStartupScreenHeldUntilAppReady) {
        // Outside the launch screen's own block, which leaves the composition - and cancels its effects - the
        // moment the app is uncovered. The two frames are there for the reason given below: the composition
        // without the launch screen has to be the one being drawn when the shell lets go, or Android's first
        // frame would be the mark after all.
        LaunchedEffect(Unit) {
            repeat(2) { withFrameNanos { } }
            onAppReady()
        }
    }
    if (!isAppReady) {
        val opacity = remember { Animatable(1f) }
        // In the desktop application the mark grows as it goes, over the slower of the two effect springs so
        // that the movement has the time to be read as one: this is the one platform where the launch screen is
        // the whole of the startup - the first frame the window paints and the last one before the app - so it
        // is worth leaving by opening into the app rather than by merely thinning out. The other three
        // open on a startup screen of their own and never watch this one go: Android's splash and the web's
        // loading page are still over it when it goes, so there it does not fade at all, and on iOS the mark
        // is already the second thing shown - so there it stays the plain, quicker dissolve, and the extra
        // frames are not spent.
        val markGrowth = if (isLaunchScreenWholeStartup) LAUNCH_MARK_EXIT_GROWTH else 0f
        LaunchScreen(
            modifier = Modifier.graphicsLayer { alpha = opacity.value.coerceIn(0f, 1f) },
            colors = launchScreenColors,
            markScale = { 1f + (1f - opacity.value.coerceIn(0f, 1f)) * markGrowth },
        )
        val motionScheme = MaterialTheme.motionScheme
        val fadeSpec = if (isLaunchScreenWholeStartup) motionScheme.slowEffectsSpec<Float>() else motionScheme.defaultEffectsSpec<Float>()
        // The icons have to be in as well, or the app would be uncovered while it is still fetching them one
        // by one and every list row, button and chip holding one would resize around it as it lands. Asking
        // for them here rather than anywhere earlier is what pays for the wait out of time the launch screen
        // was up for anyway, and on the three platforms that read a drawable without suspending this is
        // constantly true and costs nothing.
        val areDrawablesLoaded = areDrawablesLoaded()
        LaunchedEffect(arePreferencesLoaded, hasLibraryToShow, isThemeSettled, areDrawablesLoaded) {
            if (arePreferencesLoaded && hasLibraryToShow && isThemeSettled && areDrawablesLoaded) {
                if (isStartupScreenHeldUntilAppReady) {
                    // Nothing under the shell's own startup screen is seen, so the fade would only hold the
                    // app back: the app is uncovered at once, and the effect below lets the shell go.
                    isAppReady = true
                    viewModel.hasShownApp = true
                } else {
                    // Two frames rather than one, because withFrameNanos resumes while the frame it belongs
                    // to is still being assembled: the frame after it is the first one that is certainly drawn.
                    repeat(2) { withFrameNanos { } }
                    opacity.animateTo(0f, fadeSpec)
                    isAppReady = true
                    viewModel.hasShownApp = true
                    // Only now, so that the shells holding a startup screen of their own hand over to the app
                    // itself rather than to the last frames of a mark fading off it.
                    onAppReady()
                }
            }
        }
    }
}

/**
 * How much the mark has grown by the time it has faded away, on the platform that watches it leave: half as large
 * again, which is enough of a movement to be seen for what it is over the length of the fade rather than read as
 * the picture drifting.
 */
private const val LAUNCH_MARK_EXIT_GROWTH = 0.5f
