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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_frequency
import com.pandulapeter.campfire.presentation.resources.tuner_play_a_note
import com.pandulapeter.campfire.presentation.resources.tuner_reading_coarse_flat
import com.pandulapeter.campfire.presentation.resources.tuner_reading_coarse_sharp
import com.pandulapeter.campfire.presentation.resources.tuner_reading_far_flat
import com.pandulapeter.campfire.presentation.resources.tuner_reading_far_sharp
import com.pandulapeter.campfire.presentation.resources.tuner_reading_flat
import com.pandulapeter.campfire.presentation.resources.tuner_reading_in_tune
import com.pandulapeter.campfire.presentation.resources.tuner_reading_sharp
import com.pandulapeter.campfire.presentation.resources.tuner_reading_tone
import com.pandulapeter.campfire.presentation.resources.tuner_reference_tone
import com.pandulapeter.campfire.presentation.resources.tuner_target_frequency
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import com.pandulapeter.campfire.tuner.api.Pitch
import com.pandulapeter.campfire.tuner.api.model.TunerListening
import com.pandulapeter.campfire.tuner.api.model.TunerState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the tuner hears: the note in the display size with its octave beside it, a cents meter from −50 to +50 under it,
 * and under that the frequency heard and the target's. In tune, the note and the meter's marker take the second accent
 * color and the meter's notch closes into a check, so it is never said by color alone. With nothing heard the meter
 * rests and the note's place says to play one; while a reference tone sounds, it names that note and the meter rests.
 * A short window sets the note beside the meter, in one row.
 */
@Composable
internal fun TunerDisplay(
    modifier: Modifier = Modifier,
    state: TunerState,
    notation: ChordNotation,
    referencePitch: Int,
    isCompact: Boolean,
) {
    val tone = state.tone
    val reading = (state.listening as? TunerListening.Hearing)?.reading?.takeIf { tone == null }
    val note = tone ?: reading?.note
    val isInTune = reading?.isInTune == true
    val accent = animateColorAsState(if (isInTune) LocalSecondAccentColor.current else MaterialTheme.colorScheme.onSurface).value
    val exactDescription = when {
        tone != null -> stringResource(Res.string.tuner_reading_tone, noteNameWithOctave(tone, notation))
        reading == null -> stringResource(Res.string.tuner_play_a_note)
        isInTune -> stringResource(Res.string.tuner_reading_in_tune, noteNameWithOctave(reading.note, notation))
        reading.cents < 0 -> stringResource(Res.string.tuner_reading_flat, noteNameWithOctave(reading.note, notation), abs(reading.cents).roundToInt())
        else -> stringResource(Res.string.tuner_reading_sharp, noteNameWithOctave(reading.note, notation), reading.cents.roundToInt())
    }
    val current = reading?.let { TunerAnnouncement(note = it.note, offset = tunerOffsetOf(it.cents, it.isInTune)) }
    var announced by remember { mutableStateOf<TunerAnnouncement?>(null) }
    LaunchedEffect(current) {
        // Said only once it has held: a reading crossing a step and back within a breath is not news to anyone tuning. The
        // hold covers nothing heard too, so the announcement goes with the reading rather than staying over the prompt.
        delay(ANNOUNCEMENT_HOLD_MILLIS)
        announced = current
    }
    val announcedText = announced?.let {
        stringResource(
            when (it.offset) {
                TunerOffset.FAR_FLAT -> Res.string.tuner_reading_far_flat
                TunerOffset.FLAT -> Res.string.tuner_reading_coarse_flat
                TunerOffset.IN_TUNE -> Res.string.tuner_reading_in_tune
                TunerOffset.SHARP -> Res.string.tuner_reading_coarse_sharp
                TunerOffset.FAR_SHARP -> Res.string.tuner_reading_far_sharp
            },
            noteNameWithOctave(it.note, notation),
        )
    }
    val noteLabel: @Composable (Modifier) -> Unit = { labelModifier ->
        TunerNoteLabel(modifier = labelModifier, note = note, notation = notation, color = accent, isCompact = isCompact)
    }
    val meter: @Composable (Modifier) -> Unit = { meterModifier ->
        TunerMeter(
            modifier = meterModifier,
            cents = reading?.cents,
            isInTune = isInTune,
            markerColor = accent,
        )
    }
    val frequencies = @Composable {
        TunerFrequencies(
            heard = reading?.frequency,
            target = note?.let { Pitch.frequencyOf(it, referencePitch) },
            isTone = tone != null,
        )
    }
    Box(modifier = modifier) {
        // A reading every 33 ms would be a queue of speech the microphone hears too, so what is announced is a node of its
        // own that changes only with the note and a coarse step. It draws nothing, and comes first so that touch finds the
        // exact reading over it; hiding it from accessibility would silence it too.
        Box(
            modifier = Modifier.matchParentSize().clearAndSetSemantics {
                announcedText?.let { contentDescription = it }
                liveRegion = LiveRegionMode.Polite
            },
        )
        Column(
            // Explored as one, with the exact reading, rather than as the pieces it is drawn in.
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isCompact) 4.dp else 12.dp)
                .clearAndSetSemantics { contentDescription = exactDescription },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isCompact) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    noteLabel(Modifier.widthIn(min = 96.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        meter(Modifier.fillMaxWidth())
                        frequencies()
                    }
                }
            } else {
                noteLabel(Modifier)
                meter(Modifier.fillMaxWidth().padding(top = 8.dp))
                frequencies()
            }
        }
    }
}

private const val ANNOUNCEMENT_HOLD_MILLIS = 700L

/** The note and its octave, or the line asking for one, crossfading as it changes. */
@Composable
private fun TunerNoteLabel(
    modifier: Modifier,
    note: Int?,
    notation: ChordNotation,
    color: Color,
    isCompact: Boolean,
) = AnimatedContent(
    modifier = modifier.heightIn(min = if (isCompact) 56.dp else 88.dp),
    targetState = note,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    contentAlignment = Alignment.Center,
    label = "tunerNote",
) { shownNote ->
    if (shownNote == null) {
        Text(
            modifier = Modifier.padding(vertical = if (isCompact) 12.dp else 24.dp),
            text = stringResource(Res.string.tuner_play_a_note),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    } else {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = noteName(shownNote, notation),
                style = if (isCompact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                text = noteOctave(shownNote).toString(),
                style = MaterialTheme.typography.titleLarge,
                color = color,
            )
        }
    }
}

/** What is heard and what it is tuned to, or that what sounds is the reference tone. */
@Composable
private fun TunerFrequencies(
    heard: Float?,
    target: Float?,
    isTone: Boolean,
) = Row(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = when {
            isTone -> stringResource(Res.string.tuner_reference_tone)
            heard != null -> stringResource(Res.string.tuner_frequency, oneDecimal(heard))
            else -> ""
        },
        style = style,
        color = color,
    )
    Text(
        text = target?.let { stringResource(Res.string.tuner_target_frequency, oneDecimal(it)) }.orEmpty(),
        style = style,
        color = color,
    )
}

/** A frequency to a tenth of a hertz, which is a cent and a half at the low E and what the readings are steady to. */
internal fun oneDecimal(value: Float): String {
    val tenths = (value * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
