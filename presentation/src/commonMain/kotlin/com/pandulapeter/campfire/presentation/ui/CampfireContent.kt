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

import com.pandulapeter.campfire.presentation.ui.chrome.NavigationChrome
import com.pandulapeter.campfire.presentation.ui.chrome.NavigationChromeScaffold
import com.pandulapeter.campfire.presentation.ui.chrome.EXPANDED_RAIL_ITEM_LABEL_PADDING
import com.pandulapeter.campfire.presentation.ui.chrome.EXPANDED_RAIL_ITEM_MIN_HEIGHT
import com.pandulapeter.campfire.presentation.ui.chrome.RAIL_ITEM_BASE_HEIGHT
import com.pandulapeter.campfire.presentation.ui.chrome.RailCapacity
import com.pandulapeter.campfire.presentation.ui.chrome.railItemCapacity
import com.pandulapeter.campfire.presentation.ui.chrome.withMinTopEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.list.minSongColumnWidth
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.metronome.rememberMetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.export.ExportTransition
import com.pandulapeter.campfire.presentation.ui.screens.onboarding.OnboardingCelebration
import kotlinx.coroutines.flow.first

@Composable
internal fun CampfireContent(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
) {
    val backStack = viewModel.navigator.backStack
    val topLevelDestinations by viewModel.preferencesController.topLevelDestinations.collectAsStateWithLifecycle()
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
    val visibleDialog by viewModel.dialogHost.visibleDialog.collectAsStateWithLifecycle()
    val isExportMovingDeck by remember { derivedStateOf { exportTransition.progress.value > 0f } }
    val chromeInScreens = isChromeInScreens || isTopLevelScreenCovered || visibleDialog is DialogType.Export || isExportMovingDeck
    val metronomeSettings by viewModel.metronomeController.metronomeSettings.collectAsStateWithLifecycle()
    val metronomeBeat = rememberMetronomeIconBeat(
        playback = viewModel.metronomeController.metronomePlayback,
        beats = viewModel.metronomeController.metronomeBeats,
        isEnabled = metronomeSettings.isVisualBeatEnabled,
    )

    // Worked out once for both the expanded rail and the Songs screen's side panel, so that the two are never decided
    // from different widths, see minSongColumnWidth.
    // The two preferences read here are derived from the rest, so that a transposition or a fold written while a song is
    // read does not compose the app's root again.
    val userPreferences = viewModel.userPreferences.collectAsStateWithLifecycle()
    val isCoverArtEnabled by remember(userPreferences) { derivedStateOf { userPreferences.value?.isCoverArtEnabled != false } }
    val isKeyboard by remember(userPreferences) { derivedStateOf { userPreferences.value?.instrument == UserPreferences.Instrument.KEYBOARD } }
    val songColumnWidth = minSongColumnWidth(isCoverArtEnabled = isCoverArtEnabled)

    // The window's height rather than what the keyboard leaves of it, so that the grouping of the tools never flips while
    // typing, and derived, so that a window being resized recomposes only in the frame it crosses the line.
    val windowInfo = LocalWindowInfo.current
    val isShortWindow by remember(windowInfo) { derivedStateOf { windowInfo.containerDpSize.height < SHORT_WINDOW_HEIGHT } }
    // How many items each rail holds at the reader's text size, from the window's height less the rail's insets, which
    // are the system bars' and never the keyboard's. The insets are read through a state: every platform but Android
    // hands out a new, fixed insets instance on every composition (see KeyboardAwarePadding), which a derived state
    // remembered once would never see change. A line height goes through the density's font scale, non-linear included.
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val railLabelHeight = with(density) { typography.labelMedium.lineHeight.toDp() }
    val expandedRailLabelHeight = with(density) { typography.labelLarge.lineHeight.toDp() }
    val railInsets = rememberUpdatedState(NavigationRailDefaults.windowInsets.withMinTopEdge)
    val railCapacity by remember(windowInfo, density, railLabelHeight, expandedRailLabelHeight) {
        derivedStateOf {
            val insets = railInsets.value
            val railHeight = windowInfo.containerDpSize.height - with(density) { (insets.getTop(density) + insets.getBottom(density)).toDp() }
            RailCapacity(
                rail = railItemCapacity(railHeight = railHeight, itemHeight = RAIL_ITEM_BASE_HEIGHT + railLabelHeight),
                expandedRail = railItemCapacity(
                    railHeight = railHeight,
                    itemHeight = max(EXPANDED_RAIL_ITEM_MIN_HEIGHT, expandedRailLabelHeight + EXPANDED_RAIL_ITEM_LABEL_PADDING),
                ),
            )
        }
    }

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
                isShortWindow = isShortWindow,
                railCapacity = railCapacity,
                currentTopLevelDestination = backStack.lastOrNull { it is CampfireDestination.TopLevel } as? CampfireDestination.TopLevel,
                metronomeBeat = metronomeBeat,
                lastTool = viewModel.navigator.lastTool,
                isKeyboard = isKeyboard,
                onDestinationSelected = viewModel::selectTopLevelDestination,
                onToolsItemSelected = viewModel::selectToolsItem,
            )
        },
    ) { windowWidth, windowSize, chromeKind, chromeSize ->
        Box {
            CampfireScreens(
                viewModel = viewModel,
                urlOpener = urlOpener,
                windowWidth = windowWidth,
                windowSize = windowSize,
                chromeKind = chromeKind,
                chromeSize = chromeSize,
                isShortWindow = isShortWindow,
                railCapacity = railCapacity,
                songColumnWidth = songColumnWidth,
                chromeInScreens = chromeInScreens,
                exportTransition = exportTransition,
                metronomeBeat = metronomeBeat,
                onNavigationTransitionRunningChanged = { isNavigationTransitionRunning = it },
            )
            OnboardingCelebration(
                isCelebrating = viewModel.navigator.isOnboardingFinishCelebrated,
                onFinished = { viewModel.navigator.isOnboardingFinishCelebrated = false },
            )
        }
    }
}
