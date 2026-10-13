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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.minSongColumnWidth
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.metronome.rememberMetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.export.ExportTransition
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
    // The export screen is not a destination: it slides the whole deck aside instead, the chrome placed under it
    // included, so the screens draw their own copy of it from the frame it is asked for until it has slid all the way
    // off again, the way they do under a card. The copy swaps for the shared one only where both sit still.
    val exportTransition = remember { ExportTransition() }
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val isExportMovingDeck by remember { derivedStateOf { exportTransition.progress.value > 0f } }
    val chromeInScreens = isChromeInScreens || isTopLevelScreenCovered || visibleDialog is DialogType.Export || isExportMovingDeck
    val metronomeSettings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    val metronomeBeat = rememberMetronomeIconBeat(
        playback = viewModel.metronomePlayback,
        beats = viewModel.metronomeBeats,
        isEnabled = metronomeSettings.isVisualBeatEnabled,
    )

    // Worked out once for both the expanded rail and the Songs screen's side panel, so that the two are never decided
    // from different widths: a card that says less after its artist is given a narrower column, see minSongColumnWidth.
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val songColumnWidth = minSongColumnWidth(
        details = userPreferences?.songCardDetails ?: UserPreferences.CardDetails.SongsDefault,
        areChordsEnabled = userPreferences?.areChordsEnabled != false,
        isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false,
        isInSetlist = false,
    )

    NavigationChromeScaffold(
        // Painted here as well as on every screen, so that the two screens of a cross fading tab transition blend
        // into the same color they are painted in and the fade stays invisible.
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        isChromePlaced = !chromeInScreens,
        songColumnWidth = songColumnWidth,
        chrome = { chromeKind ->
            NavigationChrome(
                kind = chromeKind,
                destinations = topLevelDestinations,
                currentTopLevelDestination = backStack.lastOrNull { it is CampfireDestination.TopLevel } as? CampfireDestination.TopLevel,
                metronomeBeat = metronomeBeat,
                lastTool = viewModel.lastTool,
                isKeyboard = userPreferences?.instrument == UserPreferences.Instrument.KEYBOARD,
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
            songColumnWidth = songColumnWidth,
            chromeInScreens = chromeInScreens,
            exportTransition = exportTransition,
            metronomeBeat = metronomeBeat,
            onNavigationTransitionRunningChanged = { isNavigationTransitionRunning = it },
        )
    }
}
