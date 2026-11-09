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

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import com.pandulapeter.campfire.presentation.ui.components.CampfireTooltip
import com.pandulapeter.campfire.presentation.ui.components.FloatingControlVisibility
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.StepProgressIndicator
import org.jetbrains.compose.resources.painterResource

/**
 * The two buttons that step through the song being read: between its rows where it is read across the columns and
 * scrolls, however few of them there are, and between its sections everywhere else, paging through a row or a section
 * taller than the screen on the way (`SongStepper.isPagingBack` and `isPagingForward`, which only change what they are
 * called). Each is there only for as long as there is something of the song to step to in its direction. In a setlist
 * they go on to the song beside this one where there is nothing left in their direction ([hasPreviousSong],
 * [hasNextSong]), and turn to point the way the pager goes to say so; they only leave at the ends of the setlist. There
 * is one pair for the whole pager, answering whichever song is current, rather than one on every page sliding past with
 * it. The dots of the [stepper]'s stops take the room between them whether or not the buttons are there, so that neither
 * arriving nor leaving moves them.
 *
 * They are drawn over the song, which leaves them their room wherever it has to be scrolled, so neither ever covers a
 * line of it: the end edge of the screen, the previous one at its top and the next one at its bottom (`SongLyrics`'
 * `stepButtonInset`), or where songs are turned a column at a time ([isRow]), a row along the bottom, above the setlist's
 * pager bar, the previous one at its start and the next one at its end (the song details page's bottom padding). The
 * row is there for every song so turned, a strip or not, so paging through a setlist never moves them; it is laid out
 * in the direction a strip reads, and a strip's arrows point the way it moves.
 *
 * A song that scrolls by itself ([isNudging]) keeps the buttons, which nudge it there and still go on to the songs beside
 * it, but not the dots, whose room the controls of the scroll take (`AutoScrollControls`).
 */
@Composable
internal fun BoxScope.StepControls(
    isNudging: Boolean,
    isRow: Boolean,
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
    val isSteppedByRow = stepper?.isSteppedByRow == true
    // A quarter turn one way makes the arrow pointing down point to where the next song is, and the arrow pointing up to
    // where the previous one is, whichever way the layout reads.
    val pagingRotation = if (layoutDirection == LayoutDirection.Ltr) -90f else 90f
    // A strip turned a column at a time moves sideways, so its arrows point the way it reads: right for the next column of
    // a song read from the left, left for one read from the right.
    val stepRotation = when {
        stepper?.isAcross != true -> 0f
        stepper.readsLeftwards -> 90f
        else -> -90f
    }
    val back = StepButtonLook(
        isVisible = canStepBackInSong || hasPreviousSong,
        rotation = if (canStepBackInSong) stepRotation else pagingRotation,
        description = stringResource(
            when {
                !canStepBackInSong -> Res.string.song_details_previous_song
                isNudging || stepper?.isPagingBack == true -> Res.string.song_details_scroll_up
                isSteppedByRow -> Res.string.song_details_previous_page
                else -> Res.string.song_details_previous_section
            },
        ),
        onClick = onStepBack,
    )
    val forward = StepButtonLook(
        isVisible = canStepForwardInSong || hasNextSong,
        rotation = if (canStepForwardInSong) stepRotation else pagingRotation,
        description = stringResource(
            when {
                !canStepForwardInSong -> Res.string.song_details_next_song
                isNudging || stepper?.isPagingForward == true -> Res.string.song_details_scroll_down
                isSteppedByRow -> Res.string.song_details_next_page
                else -> Res.string.song_details_next_section
            },
        ),
        onClick = onStepForward,
    )
    if (isRow) {
        val rowDirection = when {
            stepper?.isAcross != true -> layoutDirection
            stepper.readsLeftwards -> LayoutDirection.Rtl
            else -> LayoutDirection.Ltr
        }
        StepRow(stepper = stepper, back = back, forward = forward, contentPadding = contentPadding, rowDirection = rowDirection)
    } else {
        StepColumn(stepper = stepper?.takeUnless { isNudging }, back = back, forward = forward, contentPadding = contentPadding)
    }
}

/**
 * Where [StepControls] are, and the controls of a song scrolling by itself, within pages padded by [pageContentPadding]:
 * the top button starts where the song's first row does, as the bottom one ends as far above the bottom edge as the end
 * ones are from the end of the screen, and a row of them is as far in from both sides.
 */
internal fun stepControlsPadding(pageContentPadding: PaddingValues, layoutDirection: LayoutDirection) = PaddingValues(
    start = pageContentPadding.calculateStartPadding(layoutDirection) + PAGE_HORIZONTAL_PADDING,
    top = PAGE_TOP_PADDING,
    end = pageContentPadding.calculateEndPadding(layoutDirection) + PAGE_HORIZONTAL_PADDING,
    bottom = pageContentPadding.calculateBottomPadding() + STEP_BUTTON_EDGE_MARGIN,
)

/** What one of [StepControls]' buttons shows and does. */
private class StepButtonLook(
    val isVisible: Boolean,
    val rotation: Float,
    val description: String,
    val onClick: () -> Unit,
)

@Composable
private fun BoxScope.StepColumn(
    stepper: SongStepper?,
    back: StepButtonLook,
    forward: StepButtonLook,
    contentPadding: PaddingValues,
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
        stepper = stepper,
    )
    StepButton(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = contentPadding.calculateTopPadding(), end = contentPadding.calculateEndPadding(layoutDirection)),
        look = back,
        icon = painterResource(Res.drawable.ic_move_up),
    )
    StepButton(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = contentPadding.calculateBottomPadding(), end = contentPadding.calculateEndPadding(layoutDirection)),
        look = forward,
        icon = painterResource(Res.drawable.ic_move_down),
    )
}

/** [StepControls] as a row along the bottom, laid out in [rowDirection], the previous button first. */
@Composable
private fun BoxScope.StepRow(
    stepper: SongStepper?,
    back: StepButtonLook,
    forward: StepButtonLook,
    contentPadding: PaddingValues,
    rowDirection: LayoutDirection,
) {
    val layoutDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides rowDirection) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .absolutePadding(
                    left = contentPadding.calculateLeftPadding(layoutDirection),
                    right = contentPadding.calculateRightPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Each button keeps its room while it is gone, so the dots stay centered between where the two would be.
            Box(modifier = Modifier.size(STEP_BUTTON_SIZE)) { StepButton(look = back, icon = painterResource(Res.drawable.ic_move_up)) }
            StepProgressIndicator(
                modifier = Modifier
                    .weight(1f)
                    .height(STEP_BUTTON_SIZE)
                    .padding(horizontal = STEP_BUTTON_GAP),
                stepper = stepper,
                isHorizontal = true,
            )
            Box(modifier = Modifier.size(STEP_BUTTON_SIZE)) { StepButton(look = forward, icon = painterResource(Res.drawable.ic_move_down)) }
        }
    }
}

@Composable
private fun StepButton(
    modifier: Modifier = Modifier,
    look: StepButtonLook,
    icon: Painter,
) = FloatingControlVisibility(
    modifier = modifier,
    isVisible = look.isVisible,
    shape = FloatingActionButtonDefaults.smallShape,
) {
    // The arrow only turns on a button that stays: at the end of a song or a setlist the direction changes in the very
    // frame the button starts leaving, and it would otherwise spin as it scales away. One arriving is composed afresh
    // and comes already pointing where it has to.
    val rotation = remember { Animatable(look.rotation) }
    LaunchedEffect(look.rotation, look.isVisible) {
        if (look.isVisible) rotation.animateTo(look.rotation)
    }
    CampfireTooltip(label = look.description) {
        SmallFloatingActionButton(
            // A button that has the focus takes it away with it as it leaves, which leaves nothing on the screen focused
            // and every key after that to Compose's own focus search, which scrolls the song instead of stepping it. They
            // are pressed by the same keys the screen hears anyway.
            modifier = Modifier
                .size(STEP_BUTTON_SIZE)
                .focusProperties { canFocus = false },
            onClick = look.onClick,
            // The shadow is drawn around the button by FloatingControlVisibility, which fades it with the button.
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        ) {
            Icon(
                modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
                painter = icon,
                contentDescription = look.description,
            )
        }
    }
}
