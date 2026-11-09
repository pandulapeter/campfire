/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll.AutoScrollControls
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll.SongAutoScroller
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll.SongAutoScrolling
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.PageStepper
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.stepOnTap
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.preferences.PageTurn
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.fontScale.FontScaleController
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeController
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverrides
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import com.pandulapeter.campfire.presentation.ui.state.songText.SongTextStore
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.metronome.SongTiming
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/**
 * The songs of the song details screen and the step buttons drawn over them, which are drawn over the pager rather
 * than over each page, so there is one pair of them that stays where it is while the songs slide past under it. A
 * composable of its own for the same reason as [SongDetailsAppBar]; the current page's scroll and stepper are read
 * through lambdas, since the screen keeps them in state that only the reads here and in its keys should follow.
 *
 * A song laid out in one column that scrolls by itself ([isContinuous]) has no step buttons and no dots: their column
 * holds the controls of the scroll instead ([AutoScrollControls]). Songs turned a column at a time have them in a row
 * along the bottom instead ([StepControls]).
 */
@Composable
internal fun SongDetailsPager(
    modifier: Modifier,
    contentPadding: PaddingValues,
    songTextStore: SongTextStore,
    overrides: PlayingOverrides,
    preferencesController: PreferencesController,
    fontScaleController: FontScaleController,
    songRenderer: SongRenderer,
    dialogHost: DialogHost,
    metronomeController: MetronomeController,
    onTunerClicked: () -> Unit,
    destination: CampfireDestination.SongDetails,
    importPreview: ImportPreviewState?,
    pageKey: (Song) -> String,
    songs: List<Song>,
    pagerState: PagerState,
    pageStepper: PageStepper,
    pageScrollStates: SnapshotStateMap<Int, ScrollState>,
    songTimings: SnapshotStateMap<String, SongTiming>,
    isReadOnly: Boolean,
    chordSpelling: UserPreferences.ChordSpelling,
    shouldShowChords: Boolean,
    isMetronomeEnabled: Boolean,
    isTunerEnabled: Boolean,
    isTunerSheetShown: Boolean,
    isMetronomePanelShown: Boolean,
    isBeatFlashEnabled: Boolean,
    canPage: Boolean,
    settledWidth: Dp,
    pageContentPadding: PaddingValues,
    chordDiagrams: ChordDiagrams?,
    isViewportSettled: () -> Boolean,
    currentPageScrollState: () -> ScrollState?,
    currentPageStepper: () -> SongStepper?,
    canStepBackInSong: Boolean,
    canStepForwardInSong: Boolean,
    hasPreviousSongToStepTo: Boolean,
    hasNextSongToStepTo: Boolean,
    stepBack: (from: Int?) -> Unit,
    stepForward: (from: Int?) -> Unit,
    isContinuous: Boolean,
    autoScrolling: SongAutoScrolling,
    onCurrentPage: (ScrollState, SongStepper, SongAutoScroller) -> Unit,
) = if (songs.isEmpty()) {
    // The library has not been read yet (or the screen is on its way out, see SongDetailsScreen, or a preview's import
    // is being written under an activity created again), so there is nothing to page through; a pager with no pages
    // would leave the screen blank under an app bar with no title in it.
    Box(modifier = modifier.padding(contentPadding), contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
} else Box(
    modifier = modifier
        .stepOnTap(
            pagerState = pagerState,
            isMovingFreely = {
                currentPageScrollState()?.isScrollInProgress == true && currentPageStepper()?.isStepping != true ||
                    pagerState.isScrollInProgress && !pageStepper.isStepping
            },
            isStepping = { currentPageStepper()?.isStepping == true },
            stepOrigin = { currentPageStepper()?.origin },
            stepsAcross = { currentPageStepper()?.takeIf { it.isAcross }?.readsLeftwards },
            onStep = { direction, from -> if (direction < 0) stepBack(from) else stepForward(from) },
        ),
) {
    val layoutDirection = LocalLayoutDirection.current
    SongPages(
        songTextStore = songTextStore,
        overrides = overrides,
        preferencesController = preferencesController,
        fontScaleController = fontScaleController,
        songRenderer = songRenderer,
        dialogHost = dialogHost,
        metronomeController = metronomeController,
        onTunerClicked = onTunerClicked,
        destination = destination,
        importPreview = importPreview,
        pageKey = pageKey,
        songs = songs,
        pagerState = pagerState,
        pageScrollStates = pageScrollStates,
        songTimings = songTimings,
        isReadOnly = isReadOnly,
        chordSpelling = chordSpelling,
        shouldShowChords = shouldShowChords,
        isMetronomeEnabled = isMetronomeEnabled,
        isTunerEnabled = isTunerEnabled,
        isTunerSheetShown = isTunerSheetShown,
        isMetronomePanelShown = isMetronomePanelShown,
        isBeatFlashEnabled = isBeatFlashEnabled,
        canPage = canPage,
        settledWidth = settledWidth,
        pageContentPadding = pageContentPadding,
        chordDiagrams = chordDiagrams,
        isViewportSettled = isViewportSettled,
        onCurrentPage = onCurrentPage,
    )
    val controlsPadding = stepControlsPadding(pageContentPadding, layoutDirection)
    StepControls(
        isNudging = isContinuous,
        isRow = !isContinuous && preferencesController.userPreferences.collectAsStateWithLifecycle().value?.pageTurn == PageTurn.COLUMN,
        stepper = currentPageStepper(),
        canStepBackInSong = canStepBackInSong,
        canStepForwardInSong = canStepForwardInSong,
        hasPreviousSong = hasPreviousSongToStepTo,
        hasNextSong = hasNextSongToStepTo,
        contentPadding = controlsPadding,
        onStepBack = { stepBack(null) },
        onStepForward = { stepForward(null) },
    )
    AutoScrollControls(
        isVisible = isContinuous,
        autoScrolling = autoScrolling,
        contentPadding = controlsPadding,
    )
}
