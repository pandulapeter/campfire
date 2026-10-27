/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.STEP_BUTTON_GAP
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.STEP_BUTTON_SIZE
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_auto_scroll_pause
import com.pandulapeter.campfire.presentation.resources.song_details_auto_scroll_play
import com.pandulapeter.campfire.presentation.resources.song_details_auto_scroll_speed
import com.pandulapeter.campfire.presentation.ui.components.FloatingControlVisibility
import com.pandulapeter.campfire.presentation.ui.metronome.PlayStopMark
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds

/**
 * The controls of a song laid out in one column that scrolls by itself ([isVisible]), in the column the song leaves the
 * step buttons (`SongLyrics`' `stepButtonInset`), so that none of them covers a line: the button that plays and stops
 * it just above the next button, one cluster with it, there only while there is anything left to play, and only while
 * it plays, growing up out of that button, how long the song has left and above that the slider of its speed, faster
 * downwards, the way the song moves. Paused, nothing but the button is in the column: a countdown standing still and a
 * slider by the edge would only be read as a scrollbar.
 *
 * @param contentPadding Where the step buttons are: their top, their end, and their bottom.
 */
@Composable
internal fun BoxScope.AutoScrollControls(
    isVisible: Boolean,
    autoScrolling: SongAutoScrolling,
    contentPadding: PaddingValues,
) {
    val scroller = autoScrolling.scroller
    // Worked out from the scroll on every frame, but only a new whole second recomposes what shows it: a drag, a fling,
    // a nudge, a fold, and the speed being dragged all move it, as playing does.
    val remainingSeconds = remember(scroller) {
        derivedStateOf { scroller?.remainingSeconds(autoScrolling.speed)?.let { ceil(it).toInt() } }
    }
    val isPlaying = isVisible && autoScrolling.isPlaying
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(
                top = contentPadding.calculateTopPadding() + STEP_BUTTON_SIZE + STEP_BUTTON_GAP,
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                bottom = contentPadding.calculateBottomPadding() + STEP_BUTTON_SIZE + STEP_BUTTON_GAP,
            )
            .width(STEP_BUTTON_SIZE)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            // Whatever room the buttons leave, up to the slider's own most, so that a short window still fits the button.
            modifier = Modifier.weight(1f, fill = false),
            visible = isPlaying,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(bottom = STEP_BUTTON_GAP),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpeedSlider(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = SPEED_SLIDER_MAX_HEIGHT)
                        .fillMaxHeight(),
                    speed = autoScrolling.speed,
                    onSpeedChange = autoScrolling::dragSpeed,
                    onSpeedChangeFinished = autoScrolling::writeDraggedSpeed,
                )
                Countdown(remainingSeconds = { remainingSeconds.value })
            }
        }
        PlayButton(
            // A song that fits the screen, or one already at its end, has nothing to play.
            isVisible = isVisible && (isPlaying || scroller?.canPlay == true),
            isPlaying = isPlaying,
            onClick = autoScrolling::togglePlaying,
        )
    }
}

/**
 * Plays and stops the song, coming and going as the step buttons do, in a place of its own size whether it is there or
 * not, so that neither its leaving nor its arriving moves the countdown and the slider above it.
 */
@Composable
private fun PlayButton(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) = Box(
    modifier = modifier.size(STEP_BUTTON_SIZE),
) {
    PlayButtonContent(isVisible = isVisible, isPlaying = isPlaying, onClick = onClick)
}

@Composable
private fun PlayButtonContent(
    isVisible: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) = FloatingControlVisibility(
    isVisible = isVisible,
    shape = FloatingActionButtonDefaults.smallShape,
) {
    val description = stringResource(if (isPlaying) Res.string.song_details_auto_scroll_pause else Res.string.song_details_auto_scroll_play)
    // Filled as the metronome's play button is, so that it reads as something that plays rather than as one more step.
    val colors = IconButtonDefaults.filledIconButtonColors()
    SmallFloatingActionButton(
        // Pressed by Space too, which the screen hears wherever the focus is, so the button takes none, as the step
        // buttons take none.
        modifier = Modifier
            .size(STEP_BUTTON_SIZE)
            .focusProperties { canFocus = false }
            .semantics { contentDescription = description },
        onClick = onClick,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        // The shadow is drawn around the button by FloatingControlVisibility, which fades it with the button.
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        // The triangle points down, the way playing moves the song, which also tells the button apart from the
        // metronome's, the same mark pointing the way it reads.
        PlayStopMark(
            modifier = Modifier.graphicsLayer { rotationZ = PLAY_MARK_ROTATION },
            isPlaying = isPlaying,
            size = PLAY_MARK_SIZE,
        )
    }
}

/** How long the song has left to play, read here alone, so that a new second recomposes nothing else. */
@Composable
private fun Countdown(remainingSeconds: () -> Int?) = Text(
    text = remainingSeconds()?.let { ChordProDuration.format(it.seconds) }.orEmpty(),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    maxLines = 1,
)

/**
 * The speed on a slider standing upright, faster downwards, on the logarithmic scale of [autoScrollSpeedAt]. It follows
 * [speed] whenever that changes while it is not being dragged: another song's, or the stored one arriving. Both halves
 * of its track look the same, with no stop indicator at the end: a speed has no amount to fill up to, and its middle, the
 * song's own pace, is no less than either end.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedSlider(
    modifier: Modifier = Modifier,
    speed: Int,
    onSpeedChange: (Int) -> Unit,
    onSpeedChangeFinished: () -> Unit,
) {
    val latestOnSpeedChange by rememberUpdatedState(onSpeedChange)
    val latestOnSpeedChangeFinished by rememberUpdatedState(onSpeedChangeFinished)
    val state = remember {
        SliderState(value = autoScrollSpeedFraction(speed)).apply {
            onValueChange = { fraction ->
                value = fraction
                latestOnSpeedChange(autoScrollSpeedAt(fraction))
            }
            onValueChangeFinished = { latestOnSpeedChangeFinished() }
        }
    }
    LaunchedEffect(speed) {
        if (!state.isDragging && autoScrollSpeedAt(state.value) != speed) state.value = autoScrollSpeedFraction(speed)
    }
    val speedDescription = stringResource(Res.string.song_details_auto_scroll_speed)
    val trackColor = MaterialTheme.colorScheme.secondaryContainer
    val colors = SliderDefaults.colors(
        activeTrackColor = trackColor,
        inactiveTrackColor = trackColor,
    )
    VerticalSlider(
        modifier = modifier.semantics { contentDescription = speedDescription },
        state = state,
        colors = colors,
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = colors,
                drawStopIndicator = null,
            )
        },
    )
}

private val PLAY_MARK_SIZE = 20.dp

private const val PLAY_MARK_ROTATION = 90f

/** A slider any taller only spreads the same speeds further apart. */
private val SPEED_SLIDER_MAX_HEIGHT = 280.dp
