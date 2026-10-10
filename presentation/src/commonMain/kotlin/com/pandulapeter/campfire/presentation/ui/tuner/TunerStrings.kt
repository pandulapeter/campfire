/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.tuner_in_tune
import com.pandulapeter.campfire.presentation.resources.tuner_string
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.SelectableChip
import com.pandulapeter.campfire.presentation.ui.metronome.PlayStopMark
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import org.jetbrains.compose.resources.painterResource

/**
 * One chip per string of [tuning], in the order the tuning is written and numbered down to the first, each named by its
 * note. A tap plays the string's tone, a second tap or another chip ends it; the chip of the tone sounding is selected
 * and carries the stop mark, and so is the one being heard while none sounds, so the hand finds the string the tuner is
 * reading. A string heard in tune since the microphone was opened is ticked ([tunedNotes]), so that the player sees
 * which strings are still to do.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TunerStrings(
    tuning: InstrumentTuning,
    notation: ChordNotation,
    tone: Int?,
    heardNote: Int?,
    tunedNotes: Set<Int>,
    onToggleTone: (Int) -> Unit,
) = FlowRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
) {
    val inTune = stringResource(Res.string.tuner_in_tune)
    tuning.strings.forEachIndexed { index, note ->
        val name = noteNameWithOctave(note, notation)
        val description = stringResource(Res.string.tuner_string, tuning.strings.size - index, spokenNoteName(note, notation))
        val isSounding = tone == note
        val isTuned = note in tunedNotes
        SelectableChip(
            modifier = Modifier.semantics {
                contentDescription = description
                if (isTuned) stateDescription = inTune
            },
            isSelected = if (tone != null) isSounding else heardNote == note,
            role = Role.Button,
            onClick = { onToggleTone(note) },
        ) { contentColor ->
            AnimatedVisibility(
                visible = isSounding || isTuned,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                StringMark(isSounding = isSounding, color = contentColor)
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSounding) FontWeight.Bold else null,
                color = contentColor,
            )
        }
    }
}

/** What a string's chip carries before its name: the stop mark while its tone sounds, the tick once it was in tune. */
@Composable
private fun StringMark(
    isSounding: Boolean,
    color: Color,
) = AnimatedContent(
    modifier = Modifier.padding(end = STRING_MARK_GAP),
    targetState = isSounding,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    label = "tunerStringMark",
) { sounding ->
    if (sounding) {
        PlayStopMark(isPlaying = true, size = STRING_MARK_SIZE, color = color)
    } else {
        Icon(
            modifier = Modifier.size(STRING_MARK_SIZE),
            painter = painterResource(Res.drawable.ic_check),
            contentDescription = null,
            tint = color,
        )
    }
}

private val STRING_MARK_SIZE = 18.dp
private val STRING_MARK_GAP = 6.dp
