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
import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_time_signature
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.ui.components.scaled
import com.pandulapeter.campfire.presentation.ui.metronome.TempoStepper

/**
 * The four things that decide how the song is played, in two groups after the [instrumentPill]: the transposition and
 * the capo, and the tempo and the time signature. Every line starts with a pill, and every pill at a line's start
 * stands on the keyline the section pills under it reach out to, so that the instrument pill, the transposition, and the
 * tempo line up with each other and with them. That is why the transposition has no label: it reads the key it takes
 * the chords on the page to (the semitones for a song that names no key), and the capo's label stands between the two
 * steppers. The tempo, whose pill ends in the segment that taps one in, has none either, and is followed only by the
 * time signature, where it is not 4/4. The groups share a line where they fit and are kept whole where they
 * can be ([GroupedRows]). The [tools] end the groups they belong to: the tuner after the capo (or the transposition
 * where there is no capo) unless it stands with the instrument pill, the metronome after the tempo's line (see
 * [playingToolPlaces]).
 *
 * The steppers change how the song is played where it is read and never touch the file. What the file itself
 * declares, the time signature among it, is edited in the "Song defaults" sheet the About the song sheet opens, which
 * is also where that difference is explained, so the page carries nothing but the song and its controls.
 *
 * Labels and controls grow and shrink with the lyrics, since they are part of the song's own first section — but the
 * way the sections' own header pills do rather than as a bar's buttons scaled up: what is written in them is scaled and
 * the padding around it is not, so a control is exactly as tall as the pill heading the section under it
 * ([songControlHeight], from the [titleStyle] those pills are named in). What keeps them big enough to hit at the other
 * end is `UserPreferences.MIN_FONT_SCALE`, the size below which the song details screen is not read at all.
 */
@Composable
internal fun SongPlayingControlsRow(
    modifier: Modifier = Modifier,
    controls: SongPlayingControls,
    tools: SongPlayingTools?,
    isAnimated: Boolean,
    titleStyle: TextStyle,
    fontScale: Float,
    hasInstrumentPill: Boolean,
    instrumentPill: @Composable () -> Unit,
) {
    val height = songControlHeight(titleStyle)
    val places = tools?.places(
        hasInstrumentPill = hasInstrumentPill,
        hasChordValues = controls.key != null || controls.capo != null,
        hasClickValues = controls.tempo != null || controls.timeSignature != null,
    )
    val toolButtons = @Composable { place: PlayingToolPlace, modifier: Modifier ->
        if (tools != null && places != null) {
            SongPlayingToolButtons(
                modifier = modifier,
                tools = tools,
                places = places,
                place = place,
                height = height,
                fontScale = fontScale,
            )
        }
    }
    GroupedRows(
        modifier = modifier.padding(start = 12.dp - HEADER_HORIZONTAL_PADDING, end = 12.dp),
        gap = PLAYING_CONTROL_GAP,
        groupGap = PLAYING_GROUP_GAP,
        minRowHeight = height,
        isAnimated = isAnimated,
        groups = listOf(
            instrumentPill,
            {
                ChordControls(
                    key = controls.key,
                    capo = controls.capo,
                    isAnimated = isAnimated,
                    fontScale = fontScale,
                    height = height,
                    toolButtons = toolButtons,
                )
            },
            {
                ClickControls(
                    tempo = controls.tempo,
                    timeSignature = controls.timeSignature,
                    fontScale = fontScale,
                    height = height,
                    toolButtons = toolButtons,
                )
            },
            { toolButtons(PlayingToolPlace.ALONE, Modifier.layoutId(PlayingControlId.TOOLS)) },
        ),
    )
}

/** The transposition and the capo of [SongPlayingControlsRow], the capo after its label, and the buttons ending them. */
@Composable
private fun ChordControls(
    key: SongKeyControl?,
    capo: SongCapoControl?,
    isAnimated: Boolean,
    fontScale: Float,
    height: Dp,
    toolButtons: @Composable (PlayingToolPlace, Modifier) -> Unit,
) {
    val groupEnd = Modifier.padding(start = PLAYING_CONTROL_GAP)
    val change = LocalSongChange.current
    val isLeaving = change.leaving?.chords == true
    if (key != null) {
        Row(
            modifier = Modifier
                .layoutId(PlayingControlId.KEY)
                .leavingWith(change, isLeaving),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TranspositionControls(
                transposition = key.transposition,
                isDefault = key.isDefault,
                key = key.key,
                widestValues = key.labels,
                fontScale = fontScale,
                height = height,
                onStep = key.onStep,
                onReset = key.onReset,
            )
            if (capo == null) toolButtons(PlayingToolPlace.AFTER_CHORD_VALUES, groupEnd)
        }
    }
    if (capo != null) {
        PlayingControl(
            // A label starting a line stands on the lyrics' keyline rather than on the pills'.
            modifier = Modifier
                .layoutId(PlayingControlId.CAPO)
                .leavingWith(change, isLeaving)
                .then(if (key == null) Modifier.padding(start = HEADER_HORIZONTAL_PADDING) else Modifier),
            label = stringResource(Res.string.song_editor_insert_capo),
            fontScale = fontScale,
            isAnimated = isAnimated,
            trailing = { toolButtons(PlayingToolPlace.AFTER_CHORD_VALUES, groupEnd) },
        ) {
            CapoControls(
                capo = capo.capo,
                fontScale = fontScale,
                height = height,
                onStep = capo.onStep,
                onReset = capo.onReset,
            )
        }
    }
}

/**
 * The tempo of [SongPlayingControlsRow], then the time signature where it is not 4/4, and the buttons ending them: two
 * items rather than one, so that where they do not fit on a line together the time signature goes under the stepper with
 * the metronome, instead of the line running off the page. No unit is written: a number in a stepper ending in Tap is a
 * tempo, and the stepper says "BPM" aloud.
 */
@Composable
private fun ClickControls(
    tempo: SongTempoControl?,
    timeSignature: String?,
    fontScale: Float,
    height: Dp,
    toolButtons: @Composable (PlayingToolPlace, Modifier) -> Unit,
) {
    val change = LocalSongChange.current
    val isLeaving = change.leaving?.timing == true
    if (tempo != null) {
        Row(
            modifier = Modifier
                .layoutId(PlayingControlId.TEMPO)
                .leavingWith(change, isLeaving),
            horizontalArrangement = Arrangement.spacedBy(PLAYING_CONTROL_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TempoStepper(
                bpm = tempo.tempo.bpm,
                isDefault = tempo.tempo.isDefault,
                fontScale = fontScale,
                height = height,
                onStep = tempo.onStep,
                onSet = tempo.onSet,
                onReset = tempo.onReset,
            )
            if (timeSignature == null) toolButtons(PlayingToolPlace.AFTER_CLICK_VALUES, Modifier)
        }
    }
    if (timeSignature != null) {
        Row(
            modifier = Modifier
                .layoutId(PlayingControlId.TIME)
                .leavingWith(change, isLeaving),
            horizontalArrangement = Arrangement.spacedBy(PLAYING_CONTROL_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Named aloud, since with no label beside it "3/4" alone would not say what it is.
            val spokenTimeSignature = stringResource(Res.string.song_details_time_signature, timeSignature)
            Text(
                modifier = Modifier.clearAndSetSemantics { contentDescription = spokenTimeSignature },
                text = timeSignature,
                style = MaterialTheme.typography.labelLarge.scaled(fontScale),
            )
            toolButtons(PlayingToolPlace.AFTER_CLICK_VALUES, Modifier)
        }
    }
}

private enum class PlayingControlId { KEY, CAPO, TEMPO, TIME, TOOLS }

private val PLAYING_CONTROL_GAP = 8.dp

/** Between two groups sharing a line, wider than between the controls of one, so that the line reads as the two. */
private val PLAYING_GROUP_GAP = 16.dp
