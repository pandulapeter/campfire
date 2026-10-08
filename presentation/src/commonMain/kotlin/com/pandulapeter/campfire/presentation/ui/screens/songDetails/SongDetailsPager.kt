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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_move_down
import com.pandulapeter.campfire.presentation.resources.ic_move_up
import com.pandulapeter.campfire.presentation.resources.song_details_next_page
import com.pandulapeter.campfire.presentation.resources.song_details_next_section
import com.pandulapeter.campfire.presentation.resources.song_details_next_song
import com.pandulapeter.campfire.presentation.resources.song_details_previous_page
import com.pandulapeter.campfire.presentation.resources.song_details_previous_section
import com.pandulapeter.campfire.presentation.resources.song_details_previous_song
import com.pandulapeter.campfire.presentation.resources.song_details_scroll_down
import com.pandulapeter.campfire.presentation.resources.song_details_scroll_up
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.metronome.SongTiming
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import org.jetbrains.compose.resources.painterResource

/**
 * The songs of the song details screen and the step buttons drawn over them, which are drawn over the pager rather
 * than over each page, so there is one pair of them that stays where it is while the songs slide past under it. A
 * composable of its own for the same reason as [SongDetailsAppBar]; the current page's scroll and stepper are read
 * through lambdas, since the screen keeps them in state that only the reads here and in its keys should follow.
 */
@Composable
internal fun SongDetailsPager(
    modifier: Modifier,
    contentPadding: PaddingValues,
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongDetails,
    songs: List<Song>,
    pagerState: PagerState,
    pageStepper: PageStepper,
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
    currentPageScrollState: () -> ScrollState?,
    currentPageStepper: () -> SongStepper?,
    canStepBackInSong: Boolean,
    canStepForwardInSong: Boolean,
    hasPreviousSongToStepTo: Boolean,
    hasNextSongToStepTo: Boolean,
    stepBack: (from: Int?) -> Unit,
    stepForward: (from: Int?) -> Unit,
    onCurrentPage: (ScrollState, SongStepper) -> Unit,
) = if (songs.isEmpty()) {
    // The library has not been read yet (or the screen is on its way out, see SongDetailsScreen), so there is nothing to
    // page through; a pager with no pages would leave the screen blank under an app bar with no title in it.
    Box(
        modifier = modifier.padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        DelayedLoadingIndicator()
    }
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
            onStep = { direction, from -> if (direction < 0) stepBack(from) else stepForward(from) },
        ),
) {
    val layoutDirection = LocalLayoutDirection.current
    SongPages(
        viewModel = viewModel,
        destination = destination,
        songs = songs,
        pagerState = pagerState,
        pageScrollStates = pageScrollStates,
        songTimings = songTimings,
        isReadOnly = isReadOnly,
        chordSpelling = chordSpelling,
        shouldShowChords = shouldShowChords,
        isMetronomeEnabled = isMetronomeEnabled,
        canPage = canPage,
        settledWidth = settledWidth,
        pageContentPadding = pageContentPadding,
        chordDiagrams = chordDiagrams,
        isViewportSettled = isViewportSettled,
        onCurrentPage = onCurrentPage,
    )
    // The top button starts where the song's first row does, as the bottom one ends as far above the bottom
    // edge as the end ones are from the end of the screen.
    val stepButtonsTop = PAGE_TOP_PADDING
    val stepButtonsEnd = pageContentPadding.calculateEndPadding(layoutDirection) + PAGE_HORIZONTAL_PADDING
    val stepButtonsBottom = pageContentPadding.calculateBottomPadding() + STEP_BUTTON_EDGE_MARGIN
    // The dots take the room between the two buttons whether or not the buttons are there, so that neither
    // arriving nor leaving moves them. That column is the one the song leaves the buttons, so no dot is ever
    // drawn over a line of it.
    StepProgressIndicator(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                top = stepButtonsTop + STEP_BUTTON_SIZE + STEP_BUTTON_GAP,
                end = stepButtonsEnd,
                bottom = stepButtonsBottom + STEP_BUTTON_SIZE + STEP_BUTTON_GAP,
            )
            .width(STEP_BUTTON_SIZE)
            .fillMaxHeight(),
        stepper = currentPageStepper(),
    )
    StepButtons(
        isSteppedByRow = currentPageStepper()?.isSteppedByRow == true,
        isPagingBack = currentPageStepper()?.isPagingBack == true,
        isPagingForward = currentPageStepper()?.isPagingForward == true,
        canStepBackInSong = canStepBackInSong,
        canStepForwardInSong = canStepForwardInSong,
        hasPreviousSong = hasPreviousSongToStepTo,
        hasNextSong = hasNextSongToStepTo,
        contentPadding = PaddingValues(
            top = stepButtonsTop,
            end = stepButtonsEnd,
            bottom = stepButtonsBottom,
        ),
        onStepBack = { stepBack(null) },
        onStepForward = { stepForward(null) },
    )
}

/**
 * The two buttons that step through the song being read: between its rows where it is read across the columns and
 * scrolls, however few of them there are, and between its sections everywhere else,
 * paging through a row or a section taller than the screen on the way ([isPagingBack], [isPagingForward], which only
 * change what they are called). The previous one is at the top of the end edge, the next one at its bottom, each there
 * only for as long as there is something of the song to step to in its direction. In a setlist they go on to the song beside this one where there is nothing left in their
 * direction ([hasPreviousSong], [hasNextSong]), and turn to point the way the pager goes to say so; they only leave at
 * the ends of the setlist. There is one pair for the whole pager, answering whichever song is current, rather than one
 * on every page sliding past with it.
 *
 * They are drawn over the song, which leaves them that edge of the screen wherever it has to be scrolled
 * (`SongLyrics`' `stepButtonInset`), so neither ever covers a line of it.
 */
@Composable
private fun BoxScope.StepButtons(
    isSteppedByRow: Boolean,
    isPagingBack: Boolean,
    isPagingForward: Boolean,
    canStepBackInSong: Boolean,
    canStepForwardInSong: Boolean,
    hasPreviousSong: Boolean,
    hasNextSong: Boolean,
    contentPadding: PaddingValues,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    // A quarter turn one way makes the arrow pointing down point to where the next song is, and the arrow pointing up to
    // where the previous one is, whichever way the layout reads.
    val pagingRotation = if (layoutDirection == LayoutDirection.Ltr) -90f else 90f
    StepButton(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = contentPadding.calculateTopPadding(), end = contentPadding.calculateEndPadding(layoutDirection)),
        isVisible = canStepBackInSong || hasPreviousSong,
        icon = painterResource(Res.drawable.ic_move_up),
        iconRotation = if (canStepBackInSong) 0f else pagingRotation,
        contentDescription = stringResource(
            when {
                !canStepBackInSong -> Res.string.song_details_previous_song
                isPagingBack -> Res.string.song_details_scroll_up
                isSteppedByRow -> Res.string.song_details_previous_page
                else -> Res.string.song_details_previous_section
            },
        ),
        onClick = onStepBack,
    )
    StepButton(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = contentPadding.calculateBottomPadding(), end = contentPadding.calculateEndPadding(layoutDirection)),
        isVisible = canStepForwardInSong || hasNextSong,
        icon = painterResource(Res.drawable.ic_move_down),
        iconRotation = if (canStepForwardInSong) 0f else pagingRotation,
        contentDescription = stringResource(
            when {
                !canStepForwardInSong -> Res.string.song_details_next_song
                isPagingForward -> Res.string.song_details_scroll_down
                isSteppedByRow -> Res.string.song_details_next_page
                else -> Res.string.song_details_next_section
            },
        ),
        onClick = onStepForward,
    )
}

@Composable
private fun StepButton(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    icon: Painter,
    iconRotation: Float,
    contentDescription: String,
    onClick: () -> Unit,
) = AnimatedVisibility(
    modifier = modifier,
    visible = isVisible,
    enter = scaleIn() + fadeIn(),
    exit = scaleOut() + fadeOut(),
) {
    // The arrow only turns on a button that stays: at the end of a song or a setlist the direction changes in the very
    // frame the button starts leaving, and it would otherwise spin as it scales away. One arriving is composed afresh
    // and comes already pointing where it has to.
    val rotation = remember { Animatable(iconRotation) }
    val isStaying = transition.targetState == EnterExitState.Visible
    LaunchedEffect(iconRotation, isStaying) {
        if (isStaying) rotation.animateTo(iconRotation)
    }
    SmallFloatingActionButton(
        // A button that has the focus takes it away with it as it leaves, which leaves nothing on the screen focused
        // and every key after that to Compose's own focus search, which scrolls the song instead of stepping it. They
        // are pressed by the same keys the screen hears anyway.
        modifier = Modifier
            .size(STEP_BUTTON_SIZE)
            .focusProperties { canFocus = false },
        onClick = onClick,
    ) {
        Icon(
            modifier = Modifier.rotate(rotation.value),
            painter = icon,
            contentDescription = contentDescription,
        )
    }
}

private val STEP_BUTTON_EDGE_MARGIN = 8.dp
