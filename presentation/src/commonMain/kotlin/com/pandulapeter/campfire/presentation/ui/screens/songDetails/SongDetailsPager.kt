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
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.StepProgressIndicator
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.stepOnTap
import com.pandulapeter.campfire.presentation.ui.songView.SongLyrics
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
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
 *
 * A song laid out in one column that scrolls by itself ([isContinuous]) has no step buttons and no dots: their column
 * holds the controls of the scroll instead ([AutoScrollControls]), the speed among them only where it may be changed
 * ([showsAutoScrollSpeed]), written once a drag of it ends ([onAutoScrollSpeedChosen]).
 */
@Composable
internal fun SongDetailsPager(
    modifier: Modifier,
    contentPadding: PaddingValues,
    viewModel: CampfireViewModel,
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
    showsAutoScrollSpeed: Boolean,
    onAutoScrollSpeedChosen: () -> Unit,
    onCurrentPage: (ScrollState, SongStepper, SongAutoScroller) -> Unit,
) = if (songs.isEmpty()) {
    // The library has not been read yet (or the screen is on its way out, see SongDetailsScreen, or a preview's import
    // is being written under an activity created again), so there is nothing to page through; a pager with no pages
    // would leave the screen blank under an app bar with no title in it.
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
    // The top button starts where the song's first row does, as the bottom one ends as far above the bottom
    // edge as the end ones are from the end of the screen.
    val controlsPadding = PaddingValues(
        top = PAGE_TOP_PADDING,
        end = pageContentPadding.calculateEndPadding(layoutDirection) + PAGE_HORIZONTAL_PADDING,
        bottom = pageContentPadding.calculateBottomPadding() + STEP_BUTTON_EDGE_MARGIN,
    )
    StepControls(
        isNudging = isContinuous,
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
        showsSpeed = showsAutoScrollSpeed,
        contentPadding = controlsPadding,
        onSpeedChangeFinished = onAutoScrollSpeedChosen,
    )
}

/**
 * The two buttons that step through the song being read: between its rows where it is read across the columns and
 * scrolls, however few of them there are, and between its sections everywhere else,
 * paging through a row or a section taller than the screen on the way (`SongStepper.isPagingBack` and `isPagingForward`, which only
 * change what they are called). The previous one is at the top of the end edge, the next one at its bottom, each there
 * only for as long as there is something of the song to step to in its direction. In a setlist they go on to the song beside this one where there is nothing left in their
 * direction ([hasPreviousSong], [hasNextSong]), and turn to point the way the pager goes to say so; they only leave at
 * the ends of the setlist. There is one pair for the whole pager, answering whichever song is current, rather than one
 * on every page sliding past with it.
 *
 * They are drawn over the song, which leaves them that edge of the screen wherever it has to be scrolled
 * (`SongLyrics`' `stepButtonInset`), so neither ever covers a line of it. The dots of the [stepper]'s stops take the room
 * between them whether or not the buttons are there, so that neither arriving nor leaving moves them.
 *
 * A song that scrolls by itself ([isNudging]) keeps the buttons, which nudge it there and still go on to the songs beside
 * it, but not the dots, whose room the controls of the scroll take (`AutoScrollControls`).
 */
@Composable
private fun BoxScope.StepControls(
    isNudging: Boolean,
    stepper: SongStepper?,
    canStepBackInSong: Boolean,
    canStepForwardInSong: Boolean,
    hasPreviousSong: Boolean,
    hasNextSong: Boolean,
    contentPadding: PaddingValues,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    StepProgressIndicator(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                top = contentPadding.calculateTopPadding() + STEP_BUTTON_SIZE + STEP_BUTTON_GAP,
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + STEP_BUTTON_SIZE + STEP_BUTTON_GAP,
            )
            .width(STEP_BUTTON_SIZE)
            .fillMaxHeight(),
        stepper = stepper?.takeUnless { isNudging },
    )
    val isSteppedByRow = stepper?.isSteppedByRow == true
    val isPagingBack = isNudging || stepper?.isPagingBack == true
    val isPagingForward = isNudging || stepper?.isPagingForward == true
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
            modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
            painter = icon,
            contentDescription = contentDescription,
        )
    }
}

private val STEP_BUTTON_EDGE_MARGIN = 8.dp
