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

import com.pandulapeter.campfire.presentation.ui.components.CampfireIconButton
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingTools
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.ic_back
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.fontScale.FontScaleController
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeController
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverridesSnapshot
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.state.LibraryState
import com.pandulapeter.campfire.presentation.ui.state.editor.EditorSession
import com.pandulapeter.campfire.presentation.ui.state.songText.SongTextStore
import com.pandulapeter.campfire.presentation.ui.state.songs.SongFilesController
import kotlinx.coroutines.flow.StateFlow
import com.pandulapeter.campfire.presentation.ui.components.appBar.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomePanel
import com.pandulapeter.campfire.presentation.ui.tuner.tunerAction
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.preferences.PreferencesContext
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.metronome.rememberMetronomePanelState
import com.pandulapeter.campfire.presentation.ui.screens.rememberSongActionHandler
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.painterResource

/**
 * The song details screen's app bar: the way back, the title block that opens the sheet of what the song is, the song's
 * actions and the metronome panel under them. A composable of its own because [SongDetailsScreen] recomposes on every
 * page change: what only the bar reads is collected and decided here, so that a change of it recomposes the bar rather
 * than the whole screen.
 *
 * @param currentPageScrollState Read where it is used rather than handed down as a value, so that passing the top
 *   recomposes the title alone.
 * @param metronomePanelState Created by the screen, whose pages read whether the panel is at rest, see
 *   SongDetailsPage's isViewportSettled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongDetailsAppBar(
    editorSession: EditorSession,
    songFilesController: SongFilesController,
    dialogHost: DialogHost,
    playingOverridesFlow: StateFlow<PlayingOverridesSnapshot>,
    songTextStore: SongTextStore,
    songRenderer: SongRenderer,
    libraryState: LibraryState,
    fontScaleController: FontScaleController,
    metronomeController: MetronomeController,
    onTunerClicked: () -> Unit,
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
    val songActions = rememberSongActionHandler(editorSession, songFilesController, dialogHost)
    val playingOverrides by playingOverridesFlow.collectAsStateWithLifecycle()
    val inferredKeys by songTextStore.inferredKeys.keys.collectAsStateWithLifecycle()
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
    val openPreferences = { dialogHost.showDialog(DialogType.Preferences(PreferencesContext.SONG_DETAILS)) }
    val tunerAction = if (isTunerEnabled && !shouldShowChords && !isMetronomeEnabled) {
        tunerAction(isAlwaysInMenu = isReadOnly, onClick = onTunerClicked)
    } else {
        null
    }
    CampfireTopAppBar(
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            CampfireIconButton(
                icon = painterResource(Res.drawable.ic_back),
                label = stringResource(Res.string.back),
                onClick = onBack,
            )
        },
        title = {
            SongDetailsTitle(
                currentSong = currentSong,
                setlistFileName = destination.setlistFileName,
                shouldShowChords = shouldShowChords,
                chordSpelling = chordSpelling,
                playingOverrides = playingOverrides,
                songRenderer = songRenderer,
                showsCoverInBar = showsCoverInBar,
                currentPageScrollState = currentPageScrollState,
                openCurrentSongInfo = openCurrentSongInfo,
                coroutineScope = coroutineScope,
                // Read through a lambda, so that a key arriving recomposes the title alone rather than the whole bar.
                inferredKeyOf = { fileName -> inferredKeys[fileName] },
            )
        },
        actions = {
            SongDetailsActions(
                fontScaleController = fontScaleController,
                songFileNamesInSetlistsFlow = libraryState.songFileNamesInSetlists,
                songActions = songActions,
                currentSong = currentSong,
                setlistFileName = destination.setlistFileName,
                isReadOnly = isReadOnly,
                isPerformanceModeEnabled = isPerformanceModeEnabled,
                areSetlistsEnabled = areSetlistsEnabled,
                showsFontScaleInBar = showsFontScaleInBar,
                showsSetlistAssignmentsInBar = showsSetlistAssignmentsInBar,
                aboutSongAction = openCurrentSongInfo?.let { aboutSongAction(onClick = it) },
                tunerAction = tunerAction,
                preferencesAction = preferencesAction(onClick = openPreferences),
                onReadOnlyMarkClicked = openPreferences,
            )
        },
        bottomContent = {
            val panel = rememberMetronomePanelState(metronomeController, libraryState, songTextStore)
            MetronomePanel(
                isPlaying = panel.isPlaying,
                beatLevels = panel.beatLevels,
                beats = metronomeController.metronomeBeats,
                isFlashEnabled = panel.isFlashEnabled,
                onBeatLevelsChanged = { levels -> metronomeController.setBeatLevels(panel.timeSignature, levels) },
                onPlayStop = metronomeController::toggleMetronome,
                playShortcut = KeyShortcut.METRONOME,
                isVisible = isMetronomePanelVisible,
                contentPadding = contentPadding,
                visibleState = metronomePanelState,
            )
        },
    )
}
