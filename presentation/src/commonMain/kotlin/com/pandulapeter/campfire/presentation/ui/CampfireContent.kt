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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.ui.metronome.rememberMetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.coroutines.flow.first

@Composable
internal fun CampfireContent(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
) {
    val backStack = viewModel.backStack
    val topLevelDestinations by viewModel.topLevelDestinations.collectAsStateWithLifecycle()
    var isNavigationTransitionRunning by remember { mutableStateOf(false) }
    var isChromeInScreens by remember { mutableStateOf(false) }
    val isTopLevelScreenCovered = backStack.lastOrNull() !is CampfireDestination.TopLevel
    // The chrome moves into the top level screens as soon as a card covers them, which is the frame the push starts
    // in, and moves back out only once the pop has settled. NavDisplay starts animating a pop from an effect, so its
    // transition is only reported as running a frame after the back stack changed, and reading the report any sooner
    // would take the pop for one that had already settled: the transition is given two frames to start before it is
    // waited on, and one that never starts (nothing animates behind the launch screen) is simply not waited on.
    LaunchedEffect(isTopLevelScreenCovered) {
        if (isTopLevelScreenCovered) {
            isChromeInScreens = true
        } else {
            repeat(2) { withFrameNanos { } }
            snapshotFlow { isNavigationTransitionRunning }.first { !it }
            isChromeInScreens = false
        }
    }
    val chromeInScreens = isChromeInScreens || isTopLevelScreenCovered
    val metronomeSettings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    val metronomeBeat = rememberMetronomeIconBeat(
        playback = viewModel.metronomePlayback,
        beats = viewModel.metronomeBeats,
        isEnabled = metronomeSettings.isVisualBeatEnabled,
    )

    NavigationChromeScaffold(
        // Painted here as well as on every screen, so that the two screens of a cross fading tab transition blend
        // into the same color they are painted in and the fade stays invisible.
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        isChromePlaced = !chromeInScreens,
        chrome = { chromeKind ->
            NavigationChrome(
                kind = chromeKind,
                destinations = topLevelDestinations,
                currentTopLevelDestination = backStack.lastOrNull { it is CampfireDestination.TopLevel } as? CampfireDestination.TopLevel,
                metronomeBeat = metronomeBeat,
                onDestinationSelected = viewModel::selectTopLevelDestination,
            )
        },
    ) { windowWidth, windowSize, chromeKind, chromeSize ->
        CampfireScreens(
            viewModel = viewModel,
            urlOpener = urlOpener,
            windowWidth = windowWidth,
            windowSize = windowSize,
            chromeKind = chromeKind,
            chromeSize = chromeSize,
            chromeInScreens = chromeInScreens,
            metronomeBeat = metronomeBeat,
            onNavigationTransitionRunningChanged = { isNavigationTransitionRunning = it },
        )
    }
}
