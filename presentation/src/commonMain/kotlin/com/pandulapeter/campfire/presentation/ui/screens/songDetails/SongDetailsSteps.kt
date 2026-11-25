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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.navigation.AutoScrollPlayback
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll.AutoScrollCover
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll.SongAutoScrolling
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll.rememberSongAutoScrolling
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.PageStepper
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import kotlinx.coroutines.CoroutineScope

/**
 * How the song details screen steps through its songs: the step buttons, the keys that press them, a tap on the song,
 * and the setlist's pager row, all driving the same [stepBack] and [stepForward]. [current] is the page being read, which
 * hands up its scroll and its stepper.
 */
internal data class SongDetailsSteps(
    val coroutineScope: CoroutineScope,
    val pageStepper: PageStepper,
    val current: CurrentSongPage,
    val pageScrollStates: SnapshotStateMap<Int, ScrollState>,
    val hasPreviousSongToStepTo: Boolean,
    val hasNextSongToStepTo: Boolean,
    val isContinuous: Boolean,
    val autoScrolling: SongAutoScrolling,
    val canStepBackInSong: Boolean,
    val canStepForwardInSong: Boolean,
    val nextSongConfirmation: NextSongConfirmation,
    val stepActions: SongStepActions,
    val stepBack: (from: Int?) -> Unit,
    val stepForward: (from: Int?) -> Unit,
)

/** The scroll and the stepper of the page being read, which it hands up as it becomes the current one. */
internal class CurrentSongPage {
    // The arrow keys scroll the song the reader is looking at, and the pages each scroll on their own, so the one
    // that is current hands its state up here for them to drive.
    var scrollState by mutableStateOf<ScrollState?>(null)

    // The step buttons, and the keys that press them, step between the sections or the rows of the song being read, for
    // the same reason.
    var stepper by mutableStateOf<SongStepper?>(null)
}

/**
 * The [SongDetailsSteps] of a pager ([pagerState]) through [songs], the one on screen being [currentSong]; [cover] is
 * what covers the screen, which a song scrolling by itself stops or pauses under, and [isNextSongAsked] whether a step
 * past a song's end waits for a second press (a setlist's, outside an import preview).
 */
@Composable
internal fun rememberSongDetailsSteps(
    pagerState: PagerState,
    songs: List<Song>,
    currentSong: Song?,
    userPreferences: UserPreferences?,
    cover: AutoScrollCover,
    playback: AutoScrollPlayback,
    writeSpeed: (songFileName: String, speed: Int) -> Unit,
    isNextSongAsked: Boolean,
): SongDetailsSteps {
    val canPage = songs.size > 1
    val coroutineScope = rememberCoroutineScope()
    val pageStepper = remember(pagerState, coroutineScope) { PageStepper(pagerState, coroutineScope) }
    val current = remember { CurrentSongPage() }
    val currentPageStepper = current.stepper
    // The scroll of every page that is composed, the ones beside the current page included, so that a step back from
    // the top of a song can put the one before it at its end before the pager gets there.
    val pageScrollStates = remember { mutableStateMapOf<Int, ScrollState>() }
    // The steps go on to the song beside this one at either end of it, worked out from the page being headed for like the
    // pager bar's buttons. Both are decided again at the time of the press.
    val hasPreviousSongToStepTo = canPage && pagerState.targetPage > 0
    val hasNextSongToStepTo = canPage && pagerState.targetPage < songs.lastIndex
    // A song in one column scrolls by itself, and the step buttons, their keys, and a tap only nudge it there.
    val isContinuous = userPreferences?.songLayout == SongLayout.CONTINUOUS
    val autoScrolling = rememberSongAutoScrolling(
        userPreferences = userPreferences,
        cover = cover,
        playback = playback,
        currentPage = pagerState.currentPage,
        songFileName = currentSong?.fileName,
        writeSpeed = writeSpeed,
    )
    val canStepBackInSong = if (isContinuous) autoScrolling.scroller?.canNudge(-1) == true else currentPageStepper?.canStepBack == true
    val canStepForwardInSong = if (isContinuous) autoScrolling.scroller?.canNudge(1) == true else currentPageStepper?.canStepForward == true
    val nextSongConfirmation = rememberNextSongConfirmation(
        isAsked = isNextSongAsked,
        targetPage = pagerState.targetPage,
        canStepForwardInSong = canStepForwardInSong,
    )
    val stepActions = SongStepActions(
        coroutineScope = coroutineScope,
        pagerState = pagerState,
        pageStepper = pageStepper,
        pageScrollStates = pageScrollStates,
        stepper = currentPageStepper,
        continuousScrolling = autoScrolling.takeIf { isContinuous },
        lastPage = songs.lastIndex,
        nextSongConfirmation = nextSongConfirmation,
    )
    return SongDetailsSteps(
        coroutineScope = coroutineScope,
        pageStepper = pageStepper,
        current = current,
        pageScrollStates = pageScrollStates,
        hasPreviousSongToStepTo = hasPreviousSongToStepTo,
        hasNextSongToStepTo = hasNextSongToStepTo,
        isContinuous = isContinuous,
        autoScrolling = autoScrolling,
        canStepBackInSong = canStepBackInSong,
        canStepForwardInSong = canStepForwardInSong,
        nextSongConfirmation = nextSongConfirmation,
        stepActions = stepActions,
        stepBack = rememberLatestStep(stepActions) { from -> stepBack(from) },
        stepForward = rememberLatestStep(stepActions) { from -> stepForward(from) },
    )
}
