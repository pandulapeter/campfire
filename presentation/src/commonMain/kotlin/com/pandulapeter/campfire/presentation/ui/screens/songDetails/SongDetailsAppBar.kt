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

import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingTools
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.ic_back
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.appBar.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomePanel
import com.pandulapeter.campfire.presentation.ui.tuner.tunerAction
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.metronome.rememberMetronomePanelState
import com.pandulapeter.campfire.presentation.ui.screens.rememberSongActionHandler
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.painterResource

/**
 * The song details screen's app bar: the way back, the title block that opens the sheet of what the song is, the song's
 * actions and the metronome panel under them. A composable of its own so that [SongDetailsScreen] stays small enough
 * for HotSpot to compile, which it never does for a method of more than 8,000 bytes of bytecode, and that one runs on
 * every page change. What only the bar reads is collected and decided here, so that a change of it recomposes the bar
 * rather than the whole screen.
 *
 * @param currentPageScrollState Read where it is used rather than handed down as a value, so that passing the top
 *   recomposes the title alone.
 * @param metronomePanelState Created by the screen, whose pages read whether the panel is at rest, see
 *   SongDetailsPage's isViewportSettled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongDetailsAppBar(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongDetails,
    currentSong: Song?,
    isReadOnly: Boolean,
    isPerformanceModeEnabled: Boolean,
    shouldShowChords: Boolean,
    isMetronomeEnabled: Boolean,
    isTunerEnabled: Boolean,
    areSetlistsEnabled: Boolean,
    isCoverArtEnabled: Boolean,
    chordSpelling: UserPreferences.ChordSpelling,
    currentPageScrollState: () -> ScrollState?,
    openCurrentSongInfo: (() -> Unit)?,
    songs: List<Song>,
    settledWidth: Dp,
    isMetronomePanelVisible: Boolean,
    metronomePanelState: MutableTransitionState<Boolean>,
    scrollBehavior: TopAppBarScrollBehavior?,
    contentPadding: PaddingValues,
    coroutineScope: CoroutineScope,
    onBack: () -> Unit,
) {
    val songActions = rememberSongActionHandler(viewModel)
    val playingOverrides by viewModel.playingOverrides.collectAsStateWithLifecycle()
    val layoutDirection = LocalLayoutDirection.current
    val appBarWidth = settledWidth - contentPadding.calculateStartPadding(layoutDirection) - contentPadding.calculateEndPadding(layoutDirection)
    // Decided from the settled width and for every song of the pager at once, like the song actions, so that the
    // cover and the steppers do not come and go during a navigation transition or a page change. The cover's room is
    // reserved for every song of the pager, so that paging to a song without one does not move the star in and out of
    // the menu.
    val appBarButtons = appBarButtons(appBarWidth = appBarWidth, hasCover = isCoverArtEnabled && songs.any { it.coverArtUrl != null })
    val showsCoverInBar = isCoverArtEnabled && if (isReadOnly) showsCoverInPerformanceMode(appBarWidth) else appBarButtons.isCoverShown
    val showsFontScaleInBar = if (isReadOnly) showsFontScaleInPerformanceBar(appBarWidth) else appBarButtons.isFontScaleShown
    val showsSetlistAssignmentsInBar = !isReadOnly && areSetlistsEnabled && appBarButtons.isSetlistAssignmentsShown
    // The metronome and the tuner are buttons of the song's first section (SongPlayingTools), which is there wherever
    // the chords or the metronome are switched on; with both off there is nothing to put the tuner next to, so the menu
    // holds it then.
    val tunerAction = if (isTunerEnabled && !shouldShowChords && !isMetronomeEnabled) {
        tunerAction(onClick = viewModel::openTunerSheet)
    } else {
        null
    }
    CampfireTopAppBar(
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(Res.drawable.ic_back),
                    contentDescription = stringResource(Res.string.back),
                )
            }
        },
        title = {
            SongDetailsTitle(
                currentSong = currentSong,
                setlistFileName = destination.setlistFileName,
                shouldShowChords = shouldShowChords,
                chordSpelling = chordSpelling,
                playingOverrides = playingOverrides,
                songRenderer = viewModel.songRenderer,
                showsCoverInBar = showsCoverInBar,
                currentPageScrollState = currentPageScrollState,
                openCurrentSongInfo = openCurrentSongInfo,
                coroutineScope = coroutineScope,
            )
        },
        actions = {
            SongDetailsActions(
                viewModel = viewModel,
                songActions = songActions,
                currentSong = currentSong,
                setlistFileName = destination.setlistFileName,
                isReadOnly = isReadOnly,
                isPerformanceModeEnabled = isPerformanceModeEnabled,
                areSetlistsEnabled = areSetlistsEnabled,
                showsFontScaleInBar = showsFontScaleInBar,
                showsSetlistAssignmentsInBar = showsSetlistAssignmentsInBar,
                tunerAction = tunerAction,
            )
        },
        bottomContent = {
            val panel = rememberMetronomePanelState(viewModel)
            MetronomePanel(
                isPlaying = panel.isPlaying,
                beatLevels = panel.beatLevels,
                beats = viewModel.metronomeBeats,
                isFlashEnabled = panel.isFlashEnabled,
                onBeatLevelsChanged = { levels -> viewModel.setBeatLevels(panel.timeSignature, levels) },
                onPlayStop = viewModel::toggleMetronome,
                isVisible = isMetronomePanelVisible,
                contentPadding = contentPadding,
                visibleState = metronomePanelState,
            )
        },
    )
}
