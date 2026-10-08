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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.metronome.SongTiming
import com.pandulapeter.campfire.presentation.ui.playing.effectiveCapo
import com.pandulapeter.campfire.presentation.ui.playing.effectiveTempo
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent

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
    canPage: Boolean,
    settledWidth: Dp,
    pageContentPadding: PaddingValues,
    chordDiagrams: ChordDiagrams?,
    isViewportSettled: () -> Boolean,
    onCurrentPage: (ScrollState, SongStepper) -> Unit,
) {
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val failedSongFileNames by viewModel.failedSongFileNames.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val tempos by viewModel.tempos.collectAsStateWithLifecycle()
    val capos by viewModel.capos.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    HorizontalPager(
        modifier = Modifier.bounceScrollableContent(pagerState, Orientation.Horizontal)
            .fillMaxSize()
            .fontScaleGestures(
                fontScale = { viewModel.fontScale },
                onFontScaleChanged = viewModel::setFontScale,
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
        // Only the song being read follows a pinch frame by frame; the pages beside it are composed and laid out
        // too, and take the scale once it has settled. A swipe makes its page the target, which follows at once.
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
        val tempo = effectiveTempo(song = song, setlistFileName = destination.setlistFileName, tempos = tempos)
        val capo = effectiveCapo(song = song, setlistFileName = destination.setlistFileName, capos = capos)
        SongDetailsPage(
            // Chords and annotations are drawn rather than measured, so this is what keeps anything a line
            // draws past its end off the page of the next song. The page's own padding holds the cards'
            // shadows, the header pills and the fade, so nothing that belongs to it is cut.
            modifier = Modifier.clipToBounds(),
            song = song,
            scrollState = scrollState,
            flingBehavior = flingBehavior,
            text = shownText,
            buildsModelInPlace = buildsInPlace,
            isViewportSettled = isViewportSettled,
            hasFailed = song.fileName in failedSongFileNames,
            transposition = transpositions[song.fileName, destination.setlistFileName],
            tempoOverride = tempo.takeUnless { it.isDefault }?.bpm,
            capoOverride = capo.takeUnless { it.isDefault }?.fret,
            // Read only, the four playing values are read rather than set, so the page draws them as the
            // line of text they have always been.
            playingControls = if (isReadOnly) {
                null
            } else {
                rememberSongPlayingControls(
                    viewModel = viewModel,
                    song = song,
                    setlistFileName = destination.setlistFileName,
                    transposition = transpositions[song.fileName, destination.setlistFileName],
                    chordSpelling = chordSpelling,
                    tempo = tempo,
                    capo = capo,
                    shouldShowChords = shouldShowChords,
                    shouldShowTempo = isMetronomeEnabled,
                )
            },
            shouldShowChords = shouldShowChords,
            shouldShowTempo = isMetronomeEnabled,
            shouldNumberSections = userPreferences?.shouldNumberSections == true,
            fontScale = if (isFollowingGesture) ({ viewModel.fontScale }) else ({ viewModel.settledFontScale }),
            // In a setlist the step buttons page to the songs beside every song, whether it scrolls or not.
            keepsStepButtonInset = canPage,
            // One set per song, wherever it is opened from: folding is how this reader reads it, not how the
            // setlist has the band play it.
            foldedSections = userPreferences?.foldedSections?.get(song.fileName).orEmpty(),
            onFoldToggled = { key -> viewModel.toggleSectionFold(songFileName = song.fileName, key = key) },
            chordSpelling = chordSpelling,
            settledWidth = settledWidth,
            contentPadding = pageContentPadding,
            transposeSong = viewModel.songRenderer::transposedSong,
            notateSong = viewModel.songRenderer::notatedSong,
            onRetry = { viewModel.loadSongContent(song.fileName) },
            headedOffset = { stepper.headedOffset },
            onTimingChanged = { timing -> if (timing == null) songTimings.remove(song.fileName) else songTimings[song.fileName] = timing },
            chordDiagrams = remember(chordDiagrams, song.fileName, destination.setlistFileName, isReadOnly) {
                chordDiagrams?.copy(
                    onShapesClicked = if (isReadOnly) null else {
                        { viewModel.showDialog(DialogType.ChordShapes(song = song, setlistFileName = destination.setlistFileName)) }
                    },
                )
            },
        )
    }
}
