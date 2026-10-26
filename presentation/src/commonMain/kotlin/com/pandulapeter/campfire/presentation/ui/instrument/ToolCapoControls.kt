/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveCapo
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.CapoControls
import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight

/**
 * The capo of the Chords and the Scales tabs, right after their instrument's pill (`ToolInstrumentSetting`'s trailing
 * controls, `isTrailingAfterPill`), under it where the line is too narrow: a song's capo stepper after its label, as tall as the pill beside it, one tap on the value taking
 * it off. [capo] null hides it, for an instrument the tab draws nothing for a capo to change ([chordLibraryCapo],
 * [scaleLibraryCapo]); it then fades and folds towards the pill, giving the line its room back, and comes back
 * the same way, the fret it last showed held while it leaves.
 */
@Composable
internal fun ToolCapoControls(
    modifier: Modifier = Modifier,
    capo: Int?,
    onStep: (frets: Int) -> Unit,
    onReset: () -> Unit,
) {
    val lastShown = remember { ShownCapo() }
    if (capo != null) lastShown.fret = capo
    AnimatedVisibility(
        modifier = modifier,
        visible = capo != null,
        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
        ) {
            Text(
                text = stringResource(Res.string.song_editor_insert_capo),
                style = MaterialTheme.typography.labelLarge,
            )
            CapoControls(
                // As tall as the instrument pill at the start of the line, as a song's steppers are as tall as its pill.
                height = songControlHeight(MaterialTheme.typography.titleSmall),
                capo = EffectiveCapo(fret = lastShown.fret, songFret = 0),
                onStep = onStep,
                onReset = onReset,
            )
        }
    }
}

/** The fret the stepper last showed, which it keeps showing while it leaves. Not a state: only the composition writes it. */
private class ShownCapo(var fret: Int = 0)

/** As a song's label stands before its stepper (`PlayingControl`). */
private val LABEL_GAP = 8.dp
