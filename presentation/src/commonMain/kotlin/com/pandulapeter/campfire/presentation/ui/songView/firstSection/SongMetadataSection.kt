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

import com.pandulapeter.campfire.presentation.ui.songView.LocalSongChange
import com.pandulapeter.campfire.presentation.ui.songView.leavingWith
import com.pandulapeter.campfire.presentation.ui.songView.HEADER_HORIZONTAL_PADDING
import com.pandulapeter.campfire.presentation.ui.songView.SectionMotion
import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight
import com.pandulapeter.campfire.presentation.ui.songView.model.withChordsSection
import com.pandulapeter.campfire.presentation.ui.songView.model.withMetadataSection
import com.pandulapeter.campfire.presentation.ui.songInfo.SongInfoCard
import com.pandulapeter.campfire.presentation.ui.songInfo.SongInfoEditing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
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
import com.pandulapeter.campfire.presentation.localization.textResource

/**
 * The first section of the song: the key, capo, tempo and time, which are always on the page, since they are what is
 * played, or where [isSongInfoShown] the card of what the song is, which holds them as its Song defaults tiles, headed
 * by the instrument and tuning pill where it has one. A whole, uncuttable section.
 *
 * @param playingControls What sets those four, where they are set here rather than only read: the song details screen
 * hands them down outside read only mode, see [SongPlayingControls].
 * @param playingTools The tuner and metronome buttons, which the song details screen hands down in read only mode too,
 * see [SongPlayingTools]; null in the editor's preview, which plays nothing.
 * @param readsDefaults Whether the line of text names a capo of none, and the default tempo and time signature, too, see
 * [withMetadataSection].
 * @param animatesControls Whether the controls travel to the places a new layout gives them, which the song's own
 * sections decide (see `SectionMotion`), and a window edge being dragged too, which leaves their sizes alone: never while
 * a pinch changes them on every frame.
 * @param titleStyle The style of the section titles of the lyrics, which the controls are as tall as; what the card
 * holds scales with [fontScale] the way the lyrics do.
 * @param lyricsStyle The style the lyrics are set in, already scaled, which the line of text read only mode draws the
 * four values in, so that in the lyrics' size they read as part of what is played rather than as a caption above it;
 * only the key is set as a chord is, being one (see [playingValuesText]).
 * @param instrumentPill The instrument and tuning pill that starts the section, since it is as much about how the whole
 * song is played as the controls are (see [withChordsSection]): the first of their groups, on their first line where
 * the next fits beside it; null where there is none. It fades in and out rather than the section growing towards it,
 * since the layout reads the section's size as it is measured.
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
    lyricsStyle: TextStyle,
    fontScale: Float,
    instrumentPill: (@Composable () -> Unit)? = null,
) = Column(modifier = modifier) {
    // Kept while it fades out, so that it leaves as it was rather than as nothing.
    val lastPill = remember { LastInstrumentPill() }
    instrumentPill?.let { lastPill.content = it }
    val pillVisibility = remember { MutableTransitionState(instrumentPill != null) }
        .apply { targetState = instrumentPill != null }
    // The tuner stands with the pill for as long as it is drawn, fading in and out with them, and joins the values
    // only once they have gone: one button at a time.
    val isTunerWithPill = pillVisibility.targetState || pillVisibility.currentState
    val toolHeight = songControlHeight(titleStyle)
    // A change of how the song is shown fades the pill out before the page changes and in after it has moved, so it
    // comes and goes at once then rather than fading a second time.
    val change = LocalSongChange.current
    val pillGroup = @Composable { pillModifier: Modifier ->
        AnimatedVisibility(
            modifier = Modifier.leavingWith(change, change.leaving?.instrumentPill == true),
            visibleState = pillVisibility,
            enter = if (change.isUnderWay) EnterTransition.None else fadeIn(),
            exit = if (change.isUnderWay) ExitTransition.None else fadeOut(),
        ) {
            InstrumentPillRow(
                modifier = pillModifier,
                pill = lastPill.content,
                tools = playingTools,
                toolHeight = toolHeight,
                fontScale = fontScale,
            )
        }
    }
    // The pill reaches out of the read values' keyline as far as the section pills do, so that its edge lines up with
    // theirs, counting the width it reaches out by as room to wrap the tuner in. The controls start on that keyline
    // themselves, every line of them with a pill.
    val reachingOutPill = @Composable { pillGroup(Modifier.reachingOutBy(HEADER_HORIZONTAL_PADDING)) }
    // Where nothing else is drawn in lines, the pill has a line of its own over the rest, reaching out the same way.
    if (isSongInfoShown || playingControls == null && playingTools == null) {
        pillGroup(Modifier.padding(start = 12.dp - HEADER_HORIZONTAL_PADDING, end = 12.dp, bottom = INSTRUMENT_PILL_GAP))
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
            style = lyricsStyle,
            toolHeight = toolHeight,
            fontScale = fontScale,
            hasInstrumentPill = isTunerWithPill,
            instrumentPill = reachingOutPill,
        )
    } else {
        SongPlayingControlsRow(
            controls = playingControls,
            tools = playingTools,
            isAnimated = animatesControls,
            titleStyle = titleStyle,
            fontScale = fontScale,
            hasInstrumentPill = isTunerWithPill,
            instrumentPill = { pillGroup(Modifier) },
        )
    }
}

/** The instrument and tuning [pill] with the tuner after it, which goes under it whole where it does not fit beside it. */
@Composable
private fun InstrumentPillRow(
    modifier: Modifier = Modifier,
    pill: (@Composable () -> Unit)?,
    tools: SongPlayingTools?,
    toolHeight: Dp,
    fontScale: Float,
) = FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(INSTRUMENT_PILL_GAP),
    verticalArrangement = Arrangement.spacedBy(INSTRUMENT_PILL_GAP),
) {
    pill?.invoke()
    if (tools != null) {
        SongPlayingToolButtons(
            tools = tools,
            // Only the tuner ever stands here, wherever the values put the metronome.
            places = tools.places(hasInstrumentPill = true, hasChordValues = false, hasClickValues = false),
            place = PlayingToolPlace.AFTER_INSTRUMENT_PILL,
            height = toolHeight,
            fontScale = fontScale,
        )
    }
}

private class LastInstrumentPill(var content: (@Composable () -> Unit)? = null)

/**
 * The four values read rather than set, on one line. With [tools], the key and the capo are one group and the tempo and
 * the time signature another, each ended by the buttons that belong to it (see [playingToolPlaces]), laid out like the
 * controls ([GroupedRows]) after the [instrumentPill], which the section draws over the line instead where there are no
 * tools.
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
    hasInstrumentPill: Boolean,
    instrumentPill: @Composable () -> Unit,
) {
    val chordValues = listOfNotNull(
        metadata.key?.takeIf { it.isNotBlank() }?.let { it to true },
        metadata.capo?.takeIf { readsDefaults || it != 0 }?.let { stringResource(Res.string.song_details_capo, it) to false },
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
        val places = tools.places(
            hasInstrumentPill = hasInstrumentPill,
            hasChordValues = chordValues.isNotEmpty(),
            hasClickValues = clickValues.isNotEmpty(),
        )
        GroupedRows(
            modifier = modifier.padding(horizontal = 12.dp),
            gap = INSTRUMENT_PILL_GAP,
            groupGap = PLAYING_VALUES_GROUP_GAP,
            minRowHeight = toolHeight,
            isAnimated = isAnimated,
            groups = listOf(instrumentPill) + listOf(
                PlayingToolPlace.AFTER_CHORD_VALUES to chordValues,
                PlayingToolPlace.AFTER_CLICK_VALUES to clickValues,
                PlayingToolPlace.ALONE to emptyList(),
            ).map { (place, values) ->
                {
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
            },
        )
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

/**
 * Between the instrument pill and the tuner after it, between the lines of the section, and under the pill where it has
 * a line of its own, as between the song's playing tools and between the controls of a group.
 */
private val INSTRUMENT_PILL_GAP = 8.dp
