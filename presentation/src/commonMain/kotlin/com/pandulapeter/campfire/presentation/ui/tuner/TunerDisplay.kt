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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_cents
import com.pandulapeter.campfire.presentation.resources.tuner_frequency
import com.pandulapeter.campfire.presentation.resources.tuner_in_tune
import com.pandulapeter.campfire.presentation.resources.tuner_play_a_note
import com.pandulapeter.campfire.presentation.resources.tuner_reading_flat
import com.pandulapeter.campfire.presentation.resources.tuner_reading_sharp
import com.pandulapeter.campfire.presentation.resources.tuner_reading_coarse_flat
import com.pandulapeter.campfire.presentation.resources.tuner_reading_coarse_sharp
import com.pandulapeter.campfire.presentation.resources.tuner_reading_far_flat
import com.pandulapeter.campfire.presentation.resources.tuner_reading_far_sharp
import com.pandulapeter.campfire.presentation.resources.tuner_reading_in_tune
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
 * What the tuner hears, as the instrument the page is: the note, as large as the display is wide, with its octave
 * beside it; the cents meter under it; under that the reading in words, the signed cents or that it is in tune; and
 * last, small, the frequency heard and the target's. In tune, the note and the reading take the second accent color
 * and the meter's notch closes into a check, so it is never said by color alone. With nothing heard the meter rests
 * and the note's place says to play one; while a reference tone sounds, it names that note and the meter rests.
 *
 * A short window and the sheet set the note beside the rest, in one row, at a size the row has room for.
 */
@Composable
internal fun TunerDisplay(
    modifier: Modifier = Modifier,
    state: State<TunerState>,
    notation: ChordNotation,
    referencePitch: Int,
    isCompact: Boolean,
) {
    val tunerState = state.value
    val tone = tunerState.tone
    val reading = (tunerState.listening as? TunerListening.Hearing)?.reading?.takeIf { tone == null }
    val note = tone ?: reading?.note
    val isInTune = reading?.isInTune == true
    val accent = animateColorAsState(if (isInTune) LocalSecondAccentColor.current else MaterialTheme.colorScheme.onSurface).value
    val exactDescription = when {
        tone != null -> stringResource(Res.string.tuner_reading_tone, spokenNoteName(tone, notation))
        reading == null -> stringResource(Res.string.tuner_play_a_note)
        else -> {
            val name = spokenNoteName(reading.note, notation)
            val cents = reading.cents.roundToInt()
            when {
                isInTune -> stringResource(Res.string.tuner_reading_in_tune, name)
                cents == 0 -> name
                cents < 0 -> pluralStringResource(Res.plurals.tuner_reading_flat, -cents, name, -cents)
                else -> pluralStringResource(Res.plurals.tuner_reading_sharp, cents, name, cents)
            }
        }
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
            spokenNoteName(it.note, notation),
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
            accentColor = accent,
            height = if (isCompact) COMPACT_METER_HEIGHT else METER_HEIGHT,
        )
    }
    val readingLine = @Composable {
        TunerReadingLine(
            cents = reading?.cents,
            isInTune = isInTune,
            color = accent,
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
                .padding(horizontal = 16.dp, vertical = if (isCompact) 4.dp else 16.dp)
                .clearAndSetSemantics { contentDescription = exactDescription },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isCompact) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    noteLabel(Modifier.width(COMPACT_NOTE_WIDTH * LocalDensity.current.fontScale))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        meter(Modifier.fillMaxWidth())
                        readingLine()
                        frequencies()
                    }
                }
            } else {
                noteLabel(Modifier.fillMaxWidth())
                meter(Modifier.fillMaxWidth().padding(top = 8.dp))
                readingLine()
                frequencies()
            }
        }
    }
}

private const val ANNOUNCEMENT_HOLD_MILLIS = 700L

/**
 * The compact label's width: the widest note the notations write, Latin `Sol#4`, with room to spare, and two lines of the
 * prompt. One width whatever the label says, so the meter beside it never moves as a note comes and goes; it grows with
 * the text size, since the note is never wrapped.
 */
private val COMPACT_NOTE_WIDTH = 128.dp

/**
 * The note's size on the page: as large as the display's width lets the widest note be, down to the display style's
 * own where there is no room for that, and at least as tall as that size of it, so that the prompt and a note hold the
 * same height and the meter does not move as one gives way to the other.
 */
private val NOTE_MAX_FONT_SIZE = 96.sp
private val NOTE_MIN_FONT_SIZE = 57.sp
private val NOTE_MIN_HEIGHT = 112.dp
private val COMPACT_NOTE_MIN_HEIGHT = 56.dp

private val METER_HEIGHT = 48.dp
private val COMPACT_METER_HEIGHT = 40.dp

/** The note and its octave, or the line asking for one, crossfading as it changes. */
@Composable
private fun TunerNoteLabel(
    modifier: Modifier,
    note: Int?,
    notation: ChordNotation,
    color: Color,
    isCompact: Boolean,
) = AnimatedContent(
    modifier = modifier,
    targetState = note,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    label = "tunerNote",
) { shownNote ->
    Box(
        // Each state fills the label's whole room, so that a note and the prompt are centred in the same box rather than
        // aligned inside whatever size the other left.
        modifier = Modifier.fillMaxWidth().heightIn(min = if (isCompact) COMPACT_NOTE_MIN_HEIGHT else NOTE_MIN_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        if (shownNote == null) {
            Text(
                text = stringResource(Res.string.tuner_play_a_note),
                style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                // Two lines of it stay within the compact label's minimum height, so the row keeps one height as a note arrives.
                maxLines = if (isCompact) 2 else Int.MAX_VALUE,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (isCompact) {
                    Text(
                        text = noteName(shownNote, notation),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        softWrap = false,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        // Sized to the room rather than wrapped: a note is one word, and the width it has differs between
                        // a phone held upright and the page of a tablet. The weight leaves the octave its room, and not
                        // filling it is what lets the arrangement centre the pair.
                        modifier = Modifier.weight(1f, fill = false),
                        text = noteName(shownNote, notation),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        softWrap = false,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = NOTE_MIN_FONT_SIZE, maxFontSize = NOTE_MAX_FONT_SIZE),
                    )
                }
                Text(
                    modifier = Modifier.padding(start = 2.dp, bottom = if (isCompact) 8.dp else 14.dp),
                    text = noteOctave(shownNote).toString(),
                    style = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    color = color,
                    softWrap = false,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The reading in words: how many cents off, with its sign, or that it is in tune; nothing while nothing is heard. */
@Composable
private fun TunerReadingLine(
    cents: Float?,
    isInTune: Boolean,
    color: Color,
) = AnimatedContent(
    modifier = Modifier.padding(top = 4.dp),
    targetState = when {
        cents == null -> null
        isInTune -> stringResource(Res.string.tuner_in_tune)
        else -> abs(cents.roundToInt()).let { pluralStringResource(Res.plurals.tuner_cents, it, signedCents(cents)) }
    },
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    contentAlignment = Alignment.Center,
    label = "tunerReading",
) { text ->
    Text(
        // The line keeps its height with nothing to say, so the frequencies under it do not move as a note arrives.
        text = text.orEmpty(),
        style = MaterialTheme.typography.titleMedium,
        color = if (isInTune) color else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
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
    val style = MaterialTheme.typography.labelMedium
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
