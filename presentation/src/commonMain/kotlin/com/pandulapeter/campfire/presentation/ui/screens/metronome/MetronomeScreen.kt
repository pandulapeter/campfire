/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.metronome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_start
import com.pandulapeter.campfire.presentation.resources.metronome_stop
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedScrollState
import com.pandulapeter.campfire.presentation.ui.metronome.PlayStopMark
import com.pandulapeter.campfire.presentation.ui.metronome.playStopMorphSpec
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsPage
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsWidthLayout

/**
 * The whole instrument: the tempo, the marking, the slider, tap tempo and the accents in one section, how the click is
 * counted and how it sounds in the other, side by side where the window has the room for both and stacked where it
 * does not, the way a settings tab is laid out ([SettingsPage]).
 *
 * The click is started and stopped from a button that stays at the bottom of the screen rather than from a row of the
 * page, because the page is longer than a phone's screen and a metronome that cannot be stopped without scrolling for
 * the button is no metronome. It takes the width of the page up to a cap and says what it does in words
 * ([MetronomePlayButton]): on the one screen that is nothing but an instrument, the control the whole screen is played
 * from must be the first thing a glance from a music stand finds, which a 56dp mark in the corner is not.
 *
 * The tab always shows and plays its own tempo and time signature: every way onto it clears the back stack, so no song
 * is open behind it, and a click played for a song is stopped with the song long before this screen is shown.
 * Nothing here changes a song or a setlist, so performance mode leaves all of it in place.
 */
@Composable
internal fun MetronomeScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: SettingsWidthLayout,
    scrollPosition: ScrollPosition,
    contentPadding: PaddingValues,
) {
    val playback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
    val isPlaying = playback is MetronomePlayback.Playing
    val scrollState = rememberRetainedScrollState(scrollPosition)
    Box(
        // A metronome is practised with both hands on the instrument, and a screen that dims and locks takes the beat row
        // with it.
        modifier = modifier.fillMaxSize().then(if (isPlaying) Modifier.keepScreenOn() else Modifier),
    ) {
        SettingsPage(
            sectionColumns = layout.sectionColumns,
            scrollState = scrollState,
            // The page ends above the button rather than under it, so that nothing it holds is reached through it.
            contentPadding = contentPadding.only(start = true, end = true, bottom = true, extraBottom = PLAY_BUTTON_CLEARANCE),
            section = {
                MetronomeTempoSection(
                    viewModel = viewModel,
                    playback = playback,
                )
            },
            secondSection = {
                MetronomeOptionsSection(viewModel = viewModel)
            },
        )
        MetronomePlayButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding.only(start = true, end = true, bottom = true))
                .padding(PLAY_BUTTON_MARGIN),
            isPlaying = isPlaying,
            onClick = viewModel::toggleMetronome,
        )
    }
}

/**
 * What the click is started and stopped with: one wide button at the bottom of the screen, with the morphing mark and
 * the word for what it does. Its corners round all the way into a pill while nothing is playing and square off as the
 * click runs, on the same timing the mark morphs with (`playStopMorphSpec`), so the whole button says which of the two
 * states it is in from across a room.
 *
 * It is capped at [PLAY_BUTTON_MAX_WIDTH] and centered, like the tempo above it: a maximized window would otherwise
 * hand the one control of the screen a button a meter wide.
 */
@Composable
private fun MetronomePlayButton(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val cornerRadius by animateDpAsState(
        if (isPlaying) PLAY_BUTTON_PLAYING_RADIUS else PLAY_BUTTON_HEIGHT / 2,
        playStopMorphSpec(),
    )
    Button(
        modifier = modifier
            .widthIn(max = PLAY_BUTTON_MAX_WIDTH)
            .fillMaxWidth()
            .height(PLAY_BUTTON_HEIGHT),
        shape = RoundedCornerShape(cornerRadius),
        onClick = onClick,
    ) {
        PlayStopMark(isPlaying = isPlaying, size = PLAY_MARK_SIZE)
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { playing ->
            Text(
                modifier = Modifier.padding(start = PLAY_LABEL_GAP),
                text = stringResource(if (playing) Res.string.metronome_stop else Res.string.metronome_start),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** What the button takes out of the window's edges, and the room the page leaves for it under its last row. */
private val PLAY_BUTTON_MARGIN = 16.dp
private val PLAY_BUTTON_HEIGHT = 72.dp
private val PLAY_BUTTON_CLEARANCE = PLAY_BUTTON_HEIGHT + PLAY_BUTTON_MARGIN * 2
private val PLAY_BUTTON_MAX_WIDTH = 400.dp
private val PLAY_BUTTON_PLAYING_RADIUS = 24.dp
private val PLAY_MARK_SIZE = 32.dp
private val PLAY_LABEL_GAP = 12.dp
