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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.pandulapeter.campfire.presentation.ui.components.songControlHeight
import com.pandulapeter.campfire.presentation.ui.metronome.TempoStepper

/**
 * The four things that decide how the song is played, each next to the control that sets it: the transposition, which
 * is named as such and reads the key it takes the chords on the page to, the capo and the tempo with their own
 * steppers, the tempo's pill ending in the segment that taps one in, and the time signature after them, read rather
 * than set. They are laid out in balanced rows ([BalancedRows]) — all four side by side, two and two, or one under the
 * other — rather than flowed, since three over one reads as an accident. Starting a click is the app bar's button, which is
 * in reach wherever the song has been scrolled to; this row only says what it would play.
 *
 * The steppers change how the song is played where it is read and never touch the file. What the file itself
 * declares, the time signature among it, is edited in the "Song defaults" sheet of the editing menu, which is also
 * where that difference is explained, so the page carries nothing but the song and its controls.
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
    isAnimated: Boolean,
    titleStyle: TextStyle,
    fontScale: Float,
) {
    val height = songControlHeight(titleStyle)
    BalancedRows(
        modifier = modifier.padding(horizontal = 12.dp),
        gap = PLAYING_CONTROL_GAP,
        isAnimated = isAnimated,
    ) {
        controls.key?.let { key ->
            PlayingControl(
                modifier = Modifier.layoutId(PlayingControlId.KEY),
                label = stringResource(Res.string.song_details_transposition),
                fontScale = fontScale,
            ) {
                TranspositionControls(
                    transposition = key.transposition,
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
            ) {
                TempoStepper(
                    bpm = tempo.tempo.bpm,
                    isDefault = tempo.tempo.isDefault,
                    fontScale = fontScale,
                    height = height,
                    onStep = tempo.onStep,
                    onTapped = tempo.onTapped,
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
            ) {
                Text(
                    text = timeSignature,
                    style = MaterialTheme.typography.labelLarge.scaled(fontScale),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** What [BalancedRows] follows each of [SongPlayingControlsRow]'s items by, since not all four are always among them. */
private enum class PlayingControlId { KEY, CAPO, TEMPO, TIME }

/**
 * One of [SongPlayingControlsRow]'s items: what it is and what sets it. The label is in the content color, since the
 * controls themselves already set the row apart.
 */
@Composable
private fun PlayingControl(
    modifier: Modifier = Modifier,
    label: String,
    fontScale: Float,
    control: @Composable RowScope.() -> Unit,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        modifier = Modifier.padding(end = PLAYING_CONTROL_LABEL_GAP),
        text = label,
        style = MaterialTheme.typography.labelLarge.scaled(fontScale),
    )
    control()
}

/**
 * Between two of the playing controls, and between the rows they wrap into: the gap the card's chips keep. It does not
 * grow with the song's text, any more than the gaps between its sections do.
 */
private val PLAYING_CONTROL_GAP = 8.dp
private val PLAYING_CONTROL_LABEL_GAP = 8.dp
