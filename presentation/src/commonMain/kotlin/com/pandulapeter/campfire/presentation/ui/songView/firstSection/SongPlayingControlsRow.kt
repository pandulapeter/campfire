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

import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_transposition
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_tempo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_time
import com.pandulapeter.campfire.presentation.ui.components.scaled
import com.pandulapeter.campfire.presentation.ui.metronome.TempoStepper

/**
 * The four things that decide how the song is played, each next to the control that sets it: the transposition, which
 * is named as such and reads the key it takes the chords on the page to, the capo and the tempo with their own
 * steppers, the tempo's pill ending in the segment that taps one in, and the time signature after them, read rather
 * than set. They are laid out in balanced rows ([BalancedRows]) — all four side by side, two and two, or one under the
 * other — rather than flowed, since three over one reads as an accident. The [tools] end the groups they belong to: the
 * tuner after the capo (or the transposition where there is no capo), the metronome after the time signature, so that
 * two and two they stand in a column of their own at the end of either row (see [playingToolPlaces]).
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
) {
    val height = songControlHeight(titleStyle)
    val places = tools?.places(
        hasChordValues = controls.key != null || controls.capo != null,
        hasClickValues = controls.tempo != null || controls.timeSignature != null,
    )
    val groupEnd = Modifier.padding(start = PLAYING_CONTROL_GAP)
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
    BalancedRows(
        modifier = modifier.padding(horizontal = 12.dp),
        gap = PLAYING_CONTROL_GAP,
        minRowHeight = height,
        isAnimated = isAnimated,
    ) {
        controls.key?.let { key ->
            PlayingControl(
                modifier = Modifier.layoutId(PlayingControlId.KEY),
                label = stringResource(Res.string.song_details_transposition),
                fontScale = fontScale,
                isAnimated = isAnimated,
                trailing = { if (controls.capo == null) toolButtons(PlayingToolPlace.AFTER_CHORD_VALUES, groupEnd) },
            ) {
                TranspositionControls(
                    transposition = key.transposition,
                    isDefault = key.isDefault,
                    key = key.key,
                    fontScale = fontScale,
                    height = height,
                    onStep = key.onStep,
                    onReset = key.onReset,
                )
            }
        }
        controls.capo?.let { capo ->
            PlayingControl(
                modifier = Modifier.layoutId(PlayingControlId.CAPO),
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
        controls.tempo?.let { tempo ->
            PlayingControl(
                modifier = Modifier.layoutId(PlayingControlId.TEMPO),
                label = stringResource(Res.string.song_editor_insert_tempo),
                fontScale = fontScale,
                isAnimated = isAnimated,
                trailing = { if (controls.timeSignature == null) toolButtons(PlayingToolPlace.AFTER_CLICK_VALUES, groupEnd) },
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
            }
        }
        // An item of its own rather than part of the tempo's, since it is the meter rather than how fast it goes, and
        // read rather than set: it is the one of the four that only the file says, which a control of its own on the
        // page made look like the others.
        controls.timeSignature?.let { timeSignature ->
            PlayingControl(
                modifier = Modifier.layoutId(PlayingControlId.TIME),
                label = stringResource(Res.string.song_editor_insert_time),
                fontScale = fontScale,
                isAnimated = isAnimated,
                trailing = { toolButtons(PlayingToolPlace.AFTER_CLICK_VALUES, groupEnd) },
            ) {
                Text(
                    text = timeSignature,
                    style = MaterialTheme.typography.labelLarge.scaled(fontScale),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        toolButtons(PlayingToolPlace.ALONE, Modifier.layoutId(PlayingControlId.TOOLS))
    }
}

/** What [BalancedRows] follows each of [SongPlayingControlsRow]'s items by, since not all four are always among them. */
private enum class PlayingControlId { KEY, CAPO, TEMPO, TIME, TOOLS }

/**
 * Between two of the playing controls, and between the rows they wrap into: the gap the card's chips keep. It does not
 * grow with the song's text, any more than the gaps between its sections do.
 */
private val PLAYING_CONTROL_GAP = 8.dp
