/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.firstSection

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SectionMotion
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.songControlHeight
import com.pandulapeter.campfire.presentation.ui.songView.model.withChordsSection
import com.pandulapeter.campfire.presentation.ui.songView.model.withMetadataSection
import com.pandulapeter.campfire.presentation.ui.songInfo.SongInfoCard
import com.pandulapeter.campfire.presentation.ui.songInfo.SongInfoEditing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_time
import com.pandulapeter.campfire.presentation.ui.components.textResource

/**
 * The first section of the song: the key, capo, tempo and time, which are always on the page, since they are what is
 * played, or where [isSongInfoShown] the card of what the song is, which holds them as its Song defaults tiles, headed
 * by the instrument and tuning chips where it has them. A whole, uncuttable section.
 *
 * @param playingControls What sets those four, where they are set here rather than only read: the song details screen
 * hands them down outside read only mode, see [SongPlayingControls].
 * @param playingTools The tuner and metronome buttons, which the song details screen hands down in read only mode too,
 * see [SongPlayingTools]; null in the editor's preview, which plays nothing.
 * @param readsDefaults Whether the line of text names a capo of none, and the default tempo and time signature, too, see
 * [withMetadataSection].
 * @param animatesControls Whether the controls travel to the places a new layout gives them, which the song's own
 * sections decide (see `SectionMotion`): never while a pinch or a window edge being dragged changes the layout every frame.
 * @param titleStyle The style of the section titles of the lyrics, which the controls are as tall as; what the card
 * holds scales with [fontScale] the way the lyrics do.
 * @param chordStyle The style the chords of the lyrics are set in, already scaled, which the line of text read only mode
 * draws the four values in: the key and the capo are read in the chords' accent color, and in the chords' size they read
 * as part of what is played rather than as a caption above it.
 * @param instrumentChips The instrument and tuning chips that head the section, above what they are as much about as the
 * controls are: how the whole song is played (see [withChordsSection]); null where there are none. It fades in and out rather than
 * the section growing towards it, since the layout reads the section's size as it is measured.
 */
@Composable
internal fun SongMetadataSection(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    isSongInfoShown: Boolean,
    songInfoEditing: SongInfoEditing?,
    playingControls: SongPlayingControls?,
    playingTools: SongPlayingTools?,
    readsDefaults: Boolean,
    animatesControls: Boolean,
    titleStyle: TextStyle,
    chordStyle: TextStyle,
    fontScale: Float,
    instrumentChips: (@Composable () -> Unit)? = null,
) = Column(modifier = modifier) {
    // Kept while it fades out, so that it leaves as it was rather than as nothing.
    val lastChip = remember { LastInstrumentChips() }
    instrumentChips?.let { lastChip.content = it }
    AnimatedVisibility(
        visible = instrumentChips != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        // On the controls' keyline, which the chips' names stand on by reaching out of it as the section pills do.
        FlowRow(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = INSTRUMENT_CHIP_GAP),
            horizontalArrangement = Arrangement.spacedBy(INSTRUMENT_CHIPS_GAP),
            verticalArrangement = Arrangement.spacedBy(INSTRUMENT_CHIPS_GAP),
        ) { lastChip.content?.invoke() }
    }
    if (isSongInfoShown) {
        SongInfoCard(
            metadata = metadata,
            editing = songInfoEditing,
            fontScale = fontScale,
        )
    } else if (playingControls == null) {
        SongPlayingMetadata(
            metadata = metadata,
            tools = playingTools,
            readsDefaults = readsDefaults,
            isAnimated = animatesControls,
            style = chordStyle,
            toolHeight = songControlHeight(titleStyle),
            fontScale = fontScale,
        )
    } else {
        SongPlayingControlsRow(
            controls = playingControls,
            tools = playingTools,
            isAnimated = animatesControls,
            titleStyle = titleStyle,
            fontScale = fontScale,
        )
    }
}

/** What the section's instrument chips last drew, see [SongMetadataSection]. Not a state: nothing is redrawn because of it. */
private class LastInstrumentChips(var content: (@Composable () -> Unit)? = null)

/**
 * The four values read rather than set, on one line. With [tools], the key and the capo are one group and the tempo and
 * the time signature another, each ended by the buttons that belong to it (see [playingToolPlaces]), laid out like the
 * controls: side by side where they fit, one under the other where they do not.
 */
@Composable
private fun SongPlayingMetadata(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    tools: SongPlayingTools?,
    readsDefaults: Boolean,
    isAnimated: Boolean,
    style: TextStyle,
    toolHeight: Dp,
    fontScale: Float,
) {
    val chordValues = listOfNotNull(
        metadata.key?.takeIf { it.isNotBlank() }?.let { it to true },
        metadata.capo?.takeIf { readsDefaults || it != 0 }?.let { stringResource(Res.string.song_details_capo, it) to true },
    )
    val clickValues = listOfNotNull(
        metadata.tempo?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_tempo, it) to false },
        metadata.time?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_time, it) to false },
    )
    if (tools == null) {
        SongPlayingValues(
            modifier = modifier,
            values = chordValues + clickValues,
            style = style,
        )
    } else {
        val places = tools.places(hasChordValues = chordValues.isNotEmpty(), hasClickValues = clickValues.isNotEmpty())
        BalancedRows(
            modifier = modifier.padding(horizontal = 12.dp),
            gap = PLAYING_VALUES_GROUP_GAP,
            isAnimated = isAnimated,
        ) {
            listOf(
                PlayingToolPlace.AFTER_CHORD_VALUES to chordValues,
                PlayingToolPlace.AFTER_CLICK_VALUES to clickValues,
                PlayingToolPlace.ALONE to emptyList(),
            ).forEach { (place, values) ->
                PlayingValuesGroup(
                    modifier = Modifier.layoutId(place),
                    values = values,
                    tools = tools,
                    places = places,
                    place = place,
                    style = style,
                    toolHeight = toolHeight,
                    fontScale = fontScale,
                )
            }
        }
    }
}

/** One group of [SongPlayingMetadata]'s values and the buttons that end it. Emits nothing where it has neither. */
@Composable
private fun PlayingValuesGroup(
    modifier: Modifier = Modifier,
    values: List<Pair<String, Boolean>>,
    tools: SongPlayingTools,
    places: PlayingToolPlaces,
    place: PlayingToolPlace,
    style: TextStyle,
    toolHeight: Dp,
    fontScale: Float,
) {
    val hasTools = places.tuner == place || places.metronome == place
    if (values.isNotEmpty() || hasTools) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (values.isNotEmpty()) {
                Text(
                    text = playingValuesText(values),
                    style = style,
                )
            }
            SongPlayingToolButtons(
                modifier = if (values.isEmpty()) Modifier else Modifier.padding(start = PLAYING_VALUES_TOOL_GAP),
                tools = tools,
                places = places,
                place = place,
                height = toolHeight,
                fontScale = fontScale,
            )
        }
    }
}

/** Between the two groups where they share a line: the buttons ending the first one already set them apart. */
private val PLAYING_VALUES_GROUP_GAP = 16.dp
private val PLAYING_VALUES_TOOL_GAP = 12.dp

/** Under the instrument chips, which sets them a little apart from the controls as a group of their own. */
private val INSTRUMENT_CHIP_GAP = 16.dp

/** Between the instrument chip and the tuning chip, and between their lines, as between the song's playing tools. */
private val INSTRUMENT_CHIPS_GAP = 8.dp
