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

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.rememberRowSnapFlingBehavior
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.rememberSongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.rememberSongPlayingTools
import com.pandulapeter.campfire.presentation.ui.songView.model.CHORDS_FOLD_KEY
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.songView.model.SCALES_FOLD_KEY
import com.pandulapeter.campfire.presentation.ui.songView.model.buildsModelInPlace
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import com.pandulapeter.campfire.presentation.ui.songView.model.ScaleSuggestions
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.fontScale.fontScaleGestures
import com.pandulapeter.campfire.presentation.ui.metronome.SongTiming
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.components.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.playing.playsWithoutCapo
import com.pandulapeter.campfire.presentation.ui.playing.shapeCapoFor
import com.pandulapeter.campfire.presentation.ui.playing.soundingTransposition
import com.pandulapeter.campfire.presentation.ui.playing.songPlaybackOf
import com.pandulapeter.campfire.presentation.ui.playing.tabCapoFor
import com.pandulapeter.campfire.presentation.ui.tabs.rememberTabReading

/**
 * The pages of [SongDetailsPager], one song each, with the one beside the current page on either side composed ahead of
 * time. What only the pages read is collected here, so that it is still read only inside the page that uses it.
 *
 * @param pageScrollStates The scroll of every page that is composed, by page, which a step back from the top of a song
 *   puts at the end of the one before it.
 * @param onCurrentPage Handed the scroll and the stepper of the page being read, which the keys and the step buttons
 *   drive.
 */
@Composable
internal fun SongPages(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongDetails,
    songs: List<Song>,
    pagerState: PagerState,
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
    onCurrentPage: (ScrollState, SongStepper) -> Unit,
) {
    val songTexts by viewModel.songTextStore.songTexts.collectAsStateWithLifecycle()
    val failedSongFileNames by viewModel.songTextStore.failedSongFileNames.collectAsStateWithLifecycle()
    val playingOverrides by viewModel.overrides.playingOverrides.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val tabs = rememberTabReading(userPreferences)
    val showsDegrees = chordDiagrams != null && FeatureSwitches.of(userPreferences).areChordDegreesEnabled
    val scaleSuggestions = rememberScaleSuggestions(viewModel = viewModel, userPreferences = userPreferences, shouldShowChords = shouldShowChords)
    LaunchedEffect(pagerState) {
        // A swipe right after a pinch must not hand the song it leaves the size from before the pinch once it stops being
        // the current page, halfway across: the pages beside the one being read take the new size before any of them moves.
        snapshotFlow { pagerState.isScrollInProgress }.collect { if (it) viewModel.fontScaleController.settleFontScale() }
    }
    HorizontalPager(
        modifier = Modifier.bounceScrollableContent(pagerState, Orientation.Horizontal)
            .fillMaxSize()
            .fontScaleGestures(
                fontScale = { viewModel.fontScaleController.fontScale },
                onFontScaleChanged = viewModel.fontScaleController::setFontScale,
            ),
        state = pagerState,
        key = { songs[it].fileName },
        beyondViewportPageCount = 1,
    ) { page ->
        val song = songs[page]
        val scrollState = rememberScrollState()
        DisposableEffect(page, scrollState) {
            pageScrollStates[page] = scrollState
            onDispose { if (pageScrollStates[page] === scrollState) pageScrollStates.remove(page) }
        }
        val flingBehavior = rememberRowSnapFlingBehavior(scrollState)
        val stepper = remember(scrollState, flingBehavior) { SongStepper(scrollState, flingBehavior) }
        DisposableEffect(song.fileName) {
            onDispose { songTimings.remove(song.fileName) }
        }
        if (page == pagerState.currentPage) SideEffect { onCurrentPage(scrollState, stepper) }
        // Only the song being read follows a pinch, and the viewport's height, frame by frame; the pages beside it are
        // composed and laid out too, and take the scale once it has settled, or as soon as the pager starts moving, and
        // the height the viewport last rested at. A swipe makes its page the target, which follows at once.
        val isFollowingGesture = page == pagerState.currentPage || page == pagerState.targetPage
        // Derived, so that a swipe recomposes the page only when the answer flips rather than on every frame.
        val buildsInPlace by remember(pagerState, page) {
            derivedStateOf {
                buildsModelInPlace(
                    page = page,
                    currentPage = pagerState.currentPage,
                    targetPage = pagerState.targetPage,
                    isVisible = pagerState.layoutInfo.visiblePagesInfo.any { it.index == page },
                )
            }
        }
        val text = songTexts[song.fileName]
        // The first swap of a page from loading to its lyrics lays the whole song out in one frame, so a page
        // that is neither on screen nor being swiped to is held back from it until the pager has come to rest,
        // or it would land in the middle of the settle animation. A page shows its lyrics once and keeps them,
        // a newer text included; only that first swap waits.
        var hasShownLyrics by remember { mutableStateOf(text != null) }
        val shownText = if (!hasShownLyrics && !isFollowingGesture && pagerState.isScrollInProgress) null else text
        if (shownText != null && !hasShownLyrics) SideEffect { hasShownLyrics = true }
        val foldedSections = userPreferences?.foldedSections?.get(song.fileName).orEmpty()
        val isChordSectionFolded = CHORDS_FOLD_KEY in foldedSections
        val isScaleSectionFolded = SCALES_FOLD_KEY in foldedSections
        val playback = songPlaybackOf(song = song, setlistFileName = destination.setlistFileName, overrides = playingOverrides)
        val playsWithoutCapo = userPreferences.playsWithoutCapo
        val shapeCapo = playback.shapeCapoFor(userPreferences)
        val tempo = playback.tempo
        val capo = playback.capo
        SongDetailsPage(
            // Chords and annotations are drawn rather than measured, so this is what keeps anything a line
            // draws past its end off the page of the next song. The page's own padding holds the cards'
            // shadows, the header pills and the fade, so nothing that belongs to it is cut.
            modifier = Modifier.clipToBounds(),
            song = song,
            scrollState = scrollState,
            flingBehavior = flingBehavior,
            text = shownText,
            followsViewport = isFollowingGesture,
            buildsModelInPlace = buildsInPlace,
            isViewportSettled = isViewportSettled,
            hasFailed = song.fileName in failedSongFileNames,
            transposition = playback.soundingTransposition,
            tabCapo = playback.tabCapoFor(userPreferences),
            tempoOverride = tempo.takeUnless { it.isDefault }?.bpm,
            capoOverride = capo.takeUnless { it.isDefault || playsWithoutCapo }?.fret,
            shapeCapo = shapeCapo,
            // Read only, the four playing values are read rather than set, so the page draws them as the
            // line of text they have always been.
            playingControls = if (isReadOnly) {
                null
            } else {
                rememberSongPlayingControls(
                    viewModel = viewModel,
                    song = song,
                    setlistFileName = destination.setlistFileName,
                    playback = playback,
                    userPreferences = userPreferences,
                    chordSpelling = chordSpelling,
                    shouldShowChords = shouldShowChords,
                    shouldShowTempo = isMetronomeEnabled,
                )
            },
            playingTools = rememberSongPlayingTools(
                viewModel = viewModel,
                bpm = tempo.bpm,
                isMetronomeEnabled = isMetronomeEnabled,
                isTunerEnabled = isTunerEnabled,
                isTunerSheetShown = isTunerSheetShown,
                isMetronomePanelShown = isMetronomePanelShown,
                isBeatFlashEnabled = isBeatFlashEnabled,
            ),
            shouldShowChords = shouldShowChords,
            shouldShowCapo = !playsWithoutCapo,
            shouldShowTempo = isMetronomeEnabled,
            shouldNumberSections = userPreferences?.shouldNumberSections == true,
            fontScale = if (isFollowingGesture) {
                { viewModel.fontScaleController.fontScale }
            } else {
                { viewModel.fontScaleController.settledFontScale }
            },
            // In a setlist the step buttons page to the songs beside every song, whether it scrolls or not.
            keepsStepButtonInset = canPage,
            // One set per song, wherever it is opened from: folding is how this reader reads it, not how the
            // setlist has the band play it.
            foldedSections = foldedSections,
            onFoldToggled = { key -> viewModel.preferencesController.toggleSectionFold(songFileName = song.fileName, key = key) },
            chordSpelling = chordSpelling,
            settledWidth = settledWidth,
            contentPadding = pageContentPadding,
            transposeSong = { text, transposition, capo, spelling, tabs -> viewModel.songRenderer.transposedSong(text, transposition, spelling, tabs = tabs, capo = capo) },
            notateSong = viewModel.songRenderer::notatedSong,
            onRetry = { viewModel.songTextStore.loadSongContent(song.fileName) },
            headedOffset = { stepper.headedOffset },
            onTimingChanged = { timing -> if (timing == null) songTimings.remove(song.fileName) else songTimings[song.fileName] = timing },
            chordDiagrams = remember(chordDiagrams, song.fileName, destination.setlistFileName, isReadOnly, isChordSectionFolded) {
                chordDiagrams?.copy(
                    isFolded = isChordSectionFolded,
                    onFoldToggled = { viewModel.preferencesController.toggleSectionFold(songFileName = song.fileName, key = CHORDS_FOLD_KEY) },
                    onChordClicked = if (isReadOnly) null else {
                        { chordName -> viewModel.dialogHost.showDialog(DialogType.ChordShape(song = song, setlistFileName = destination.setlistFileName, chordName = chordName)) }
                    },
                    // Read only mode keeps them: they change this device's preference rather than the song or the setlist.
                    onInstrumentClicked = { viewModel.dialogHost.showDialog(DialogType.Instrument) },
                    onTuningClicked = { viewModel.dialogHost.showDialog(DialogType.Tuning) },
                )
            },
            tabs = tabs,
            showsDegrees = showsDegrees,
            scaleSuggestions = remember(scaleSuggestions, song.fileName, isScaleSectionFolded, shapeCapo) {
                scaleSuggestions?.copy(
                    isFolded = isScaleSectionFolded,
                    capo = shapeCapo,
                    onFoldToggled = { viewModel.preferencesController.toggleSectionFold(songFileName = song.fileName, key = SCALES_FOLD_KEY) },
                )
            },
        )
    }
}

/**
 * What the Scales section of every page is drawn with, null where it is off: with the chords, since the scales are
 * read from them, and with the Scale suggestions switch, which the Scales switch takes with it. One for the whole pager,
 * since nothing else in it belongs to one song: whether the section is folded is each song's own, given by its page.
 */
@Composable
private fun rememberScaleSuggestions(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
    shouldShowChords: Boolean,
): ScaleSuggestions? {
    if (!shouldShowChords || !FeatureSwitches.of(userPreferences).areScaleSuggestionsEnabled) return null
    val notation = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation.toChordNotation()
    return remember(notation) {
        ScaleSuggestions(
            onScaleClicked = { scaleId, rootName, capo -> viewModel.dialogHost.showDialog(DialogType.Scale(scaleId = scaleId, rootName = rootName, capo = capo)) },
            notation = notation,
        )
    }
}
