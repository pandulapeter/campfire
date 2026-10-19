/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.scales.PlayableScales
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_chord_scales

/**
 * The end of a chord's sheet: the scales to play over it on its root ([PlayableScales.over]), each a
 * chip naming it in the key of the chord's root as the chord spells it, so that a `D#m` suggests D♯ scales and an `Ebm`
 * E♭ ones. A chip hands the scale's id and that root to [onScaleSelected]. A chord no scale holds composes nothing: the
 * row is a suggestion, and saying there is none would only push the shapes up for nothing.
 */
@Composable
internal fun ScaleChipRow(
    modifier: Modifier = Modifier,
    chord: SongChord,
    notation: ChordNotation,
    onScaleSelected: (scaleId: String, rootName: String) -> Unit,
) {
    val scales = remember(chord.chord) { PlayableScales.over(chord.chord) }
    if (scales.isEmpty()) return
    ScaleChips(
        modifier = modifier.padding(top = 16.dp),
        title = stringResource(Res.string.song_details_chord_scales),
        scales = scales,
        rootName = chordRootName(chord),
        notation = notation,
        onScaleSelected = onScaleSelected,
    )
}

/**
 * The root of [chord] as its name spells it, in the standard notation (`Eb` for `Ebm7`, `F#` for `F#/A#`): what a
 * scale suggested for it is played on. A chord with no spelling of its own is named by its root as a sharp.
 */
internal fun chordRootName(chord: SongChord): String {
    val spelling = chord.spelling?.takeIf { it.firstOrNull()?.uppercaseChar() in 'A'..'G' }
        ?: return sharpNames[chord.chord.root.mod(12)]
    val accidental = spelling.getOrNull(1)?.takeIf { it == '#' || it == 'b' }
    return spelling.first().uppercaseChar() + (accidental?.toString() ?: "")
}

private val sharpNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
