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

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.metronome.SongTiming
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/**
 * Reads the text of the song being read, and of the songs beside it: the pages next to the current one are composed
 * ahead of time (beyondViewportPageCount), so their text is read ahead of time too, and a swipe then lands on lyrics
 * rather than on a loading indicator.
 */
@Composable
internal fun LoadSongTexts(
    viewModel: CampfireViewModel,
    songs: List<Song>,
    currentPage: Int,
    songTexts: Map<String, String>,
) {
    val failedSongFileNames by viewModel.songTextStore.failedSongFileNames.collectAsStateWithLifecycle()
    val currentFileName = songs.getOrNull(currentPage)?.fileName
    LaunchedEffect(currentFileName) { currentFileName?.let(viewModel.songTextStore::loadSongContent) }
    LaunchedEffect(currentPage, songs) {
        listOfNotNull(songs.getOrNull(currentPage - 1), songs.getOrNull(currentPage + 1))
            .filter { it.fileName !in songTexts && it.fileName !in failedSongFileNames }
            .forEach { viewModel.songTextStore.loadSongContent(it.fileName) }
    }
}

/**
 * Scrolls a pager created before the library was read to the song that was tapped, and tells the view model where the
 * pager is: the page it has come to rest on and the page it is headed for. Not part of [SongDetailsScreen] itself,
 * which has to stay small enough for HotSpot to compile.
 */
@Composable
internal fun FollowPager(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongDetails,
    songs: List<Song>,
    pagerState: PagerState,
    songTimings: SnapshotStateMap<String, SongTiming>,
) {
    // The initial page is only read when the pager is created. If the library had not been read by then, the pager
    // was created with no pages and its first page is page zero: the song that was tapped is scrolled to once the
    // songs are there, exactly once.
    var isInitialPageSettled by rememberSaveable { mutableStateOf(songs.isNotEmpty()) }
    LaunchedEffect(songs.size) {
        if (!isInitialPageSettled && songs.isNotEmpty()) {
            isInitialPageSettled = true
            pagerState.scrollToPage(destination.initialIndex.coerceIn(0, songs.lastIndex))
        }
    }
    // Where the pager has come to rest is where the user is, which the web build's address names. Not before the
    // initial page has been scrolled to, or the first page of a pager created before the library was read would be
    // reported on its way to the song that was tapped.
    val latestSongs by rememberUpdatedState(songs)
    val latestDestination by rememberUpdatedState(destination)
    LaunchedEffect(pagerState, isInitialPageSettled) {
        if (isInitialPageSettled) {
            snapshotFlow { latestSongs.getOrNull(pagerState.settledPage)?.fileName }.collect { fileName ->
                if (fileName != null) viewModel.navigator.onSongDetailsPageSettled(latestDestination, fileName)
            }
        }
    }
    // The page being headed for rather than the one settled on, so that a click paged on to the next song has its tempo
    // while the page is still sliding in, and the stretch of it that page is headed for. A song paged back to is put at
    // its end first (stepBack), so the click lands on its last stretch rather than on its opening.
    LaunchedEffect(pagerState, isInitialPageSettled) {
        if (isInitialPageSettled) {
            snapshotFlow { latestSongs.getOrNull(pagerState.targetPage)?.fileName?.let { it to songTimings[it] } }.collect { target ->
                if (target != null) viewModel.metronomeController.onSongDetailsPageChanged(latestDestination, target.first, target.second)
            }
        }
    }
}
