/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.metronome.api.TapTempo
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_metronome
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.metronome_beat_unit
import com.pandulapeter.campfire.presentation.resources.metronome_beat_unit_decrease
import com.pandulapeter.campfire.presentation.resources.metronome_beat_unit_increase
import com.pandulapeter.campfire.presentation.resources.metronome_beats_decrease
import com.pandulapeter.campfire.presentation.resources.metronome_beats_increase
import com.pandulapeter.campfire.presentation.resources.metronome_beats_per_bar
import com.pandulapeter.campfire.presentation.resources.metronome_tap
import com.pandulapeter.campfire.presentation.resources.metronome_tap_description
import com.pandulapeter.campfire.presentation.resources.song_details_metronome_hide
import com.pandulapeter.campfire.presentation.resources.song_details_metronome_show
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_increase
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_reset
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.KEY_SEPARATOR
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.MenuStepperRow
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.STEPPER_HEIGHT
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.Stepper
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.scaled
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlin.math.floor
import org.jetbrains.compose.resources.painterResource

/**
 * The play and stop mark of both metronomes' buttons, morphing from one into the other: the triangle's three corners
 * (one of them doubled) travel to the square's four, so the change reads as one shape becoming another rather than as
 * two icons swapping.
 */
@Composable
internal fun PlayStopMark(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    size: Dp = 24.dp,
    color: Color = LocalContentColor.current,
) {
    // The mark turns the way the setlist assignments star does, a quarter turn clockwise whichever way it is going, but
    // easing in rather than leaning back and overshooting: a press that starts or stops the click is answered at once
    // and ends on the beat, where a bounce would blur when it took effect. The morph runs over the same turn. A square looks the same a quarter turn back and the triangle a whole turn back, so a turn that starts
    // from rest is first snapped to the angle it would have to start from to end upright, which is what lets stop turn
    // into play clockwise too. A change that arrives mid-turn carries on clockwise from wherever the mark is, to the next
    // angle the new shape rests upright at.
    val progress by animateFloatAsState(if (isPlaying) 1f else 0f, playStopMorphSpec())
    val turn = remember { Animatable(0f) }
    var turnTarget by remember { mutableFloatStateOf(0f) }
    var turnedFor by remember { mutableStateOf(isPlaying) }
    LaunchedEffect(isPlaying) {
        if (turnedFor != isPlaying) {
            turnedFor = isPlaying
            if (turn.value == turnTarget) {
                turnTarget = if (isPlaying) 0f else -PLAY_STOP_TURN
                turn.snapTo(turnTarget)
            }
            turnTarget = if (isPlaying) turnTarget + PLAY_STOP_TURN else (floor(turnTarget / FULL_TURN) + 1) * FULL_TURN
            turn.animateTo(
                targetValue = turnTarget,
                animationSpec = tween(
                    durationMillis = PLAY_STOP_TURN_DURATION,
                    easing = FastOutLinearInEasing,
                ),
            )
        }
    }
    Canvas(modifier = modifier.size(size).graphicsLayer { rotationZ = turn.value }) {
        val unit = this.size.minDimension / 24f
        val path = Path()
        PLAY_CORNERS.indices.forEach { index ->
            val point = lerp(PLAY_CORNERS[index], STOP_CORNERS[index], progress) * unit
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawPath(path, color)
    }
}

/**
 * The timing of [PlayStopMark]'s morph, which the Metronome tab's button squares its corners off on as well: the length of
 * the turn, on an ordinary easing, since easing in as well would leave the shape unchanged until the turn is nearly over.
 */
internal fun <T> playStopMorphSpec(): FiniteAnimationSpec<T> = tween(durationMillis = PLAY_STOP_TURN_DURATION)

/** How far [PlayStopMark] turns between its two states, in degrees, and how long that takes in milliseconds. */
private const val PLAY_STOP_TURN = 90f
private const val PLAY_STOP_TURN_DURATION = 250
private const val FULL_TURN = 360f

private fun lerp(start: Offset, stop: Offset, fraction: Float) = Offset(
    x = start.x + (stop.x - start.x) * fraction,
    y = start.y + (stop.y - start.y) * fraction,
)

/** Material's play triangle on its 24-unit grid, the tip doubled so that it can become two corners of the square. */
private val PLAY_CORNERS = listOf(Offset(8f, 5f), Offset(19f, 12f), Offset(19f, 12f), Offset(8f, 19f))
private val STOP_CORNERS = listOf(Offset(6f, 6f), Offset(18f, 6f), Offset(18f, 18f), Offset(6f, 18f))

/**
 * The song details screen's metronome: the mark that shows and hides the panel the click is played from, in the second
 * accent color - the color of what is played - while the panel is up, and pulsing on every heard beat (a little larger
 * on an accent) unless the flash is off. While the panel is up that pulse is the growing alone, the mark being in the
 * accent color already and the panel's own beat row being right under it. The content description says the tempo the click
 * would start at, since that is all a screen reader user would otherwise not know.
 */
@Composable
internal fun MetronomeButton(
    modifier: Modifier = Modifier,
    isPanelShown: Boolean,
    isPlaying: Boolean,
    bpm: Int,
    beats: Flow<MetronomeBeat>,
    isFlashEnabled: Boolean,
    onClick: () -> Unit,
) {
    val pulse = remember { Animatable(0f) }
    // Not gated on isPlaying, as the beat row is not: the first beat can be heard before the composition knows the click
    // plays, and the engine emits no beat while it does not.
    val shouldFlash by rememberUpdatedState(isFlashEnabled)
    LaunchedEffect(beats) {
        // The latest beat cuts the fade of the one before it short, so that a fade longer than a beat of a fast tempo
        // never leaves the pulse behind the click.
        beats.filter { !it.isSubdivision }.collectLatest { beat ->
            if (shouldFlash) {
                pulse.snapTo(if (beat.level == BeatLevel.ACCENT) 1f else 0.6f)
                pulse.animateTo(0f)
            }
        }
    }
    LaunchedEffect(isPlaying) { if (!isPlaying) pulse.snapTo(0f) }
    val accentColor = LocalSecondAccentColor.current
    val baseColor by animateColorAsState(if (isPanelShown) accentColor else LocalContentColor.current)
    IconButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Icon(
            modifier = Modifier.graphicsLayer {
                val scale = 1f + pulse.value * PULSE_SCALE
                scaleX = scale
                scaleY = scale
            },
            painter = painterResource(Res.drawable.ic_metronome),
            contentDescription = metronomePanelLabel(isPanelShown = isPanelShown, bpm = bpm),
            tint = lerp(baseColor, accentColor, pulse.value.coerceIn(0f, 1f)),
        )
    }
}

private const val PULSE_SCALE = 0.25f

/** [MetronomeButton] as an entry of the song details overflow menu, for a bar that has no room for the button. */
@Composable
internal fun metronomeAction(
    isPanelShown: Boolean,
    bpm: Int,
    onClick: () -> Unit,
) = ActionsMenuItem(
    title = metronomePanelLabel(isPanelShown = isPanelShown, bpm = bpm),
    icon = painterResource(Res.drawable.ic_metronome),
    key = METRONOME_ACTION_KEY,
    onClick = onClick,
)

private const val METRONOME_ACTION_KEY = "metronome"

/** What showing or hiding the panel is called, with the tempo a click started from it would play at while it is hidden. */
@Composable
private fun metronomePanelLabel(isPanelShown: Boolean, bpm: Int) = if (isPanelShown) {
    stringResource(Res.string.song_details_metronome_hide)
} else {
    "${stringResource(Res.string.song_details_metronome_show)} $KEY_SEPARATOR ${stringResource(Res.string.song_details_tempo, bpm.toString())}"
}

/**
 * The tempo stepper of a song, the transposition's twin gesture for gesture: highlighted while the song plays at a
 * tempo of its own here, and one tap on the value puts it back to the file's. Its buttons repeat while held, since a
 * tempo is often far from where it starts.
 *
 * @param onTapped Taps a tempo in, as a segment of the pill after the two buttons where it is given: tapping along
 * with a song sets the very number the stepper steps, so the two are one control rather than a stepper with a loose
 * word of a button beside it.
 */
@Composable
internal fun TempoStepper(
    modifier: Modifier = Modifier,
    tempo: EffectiveTempo,
    fontScale: Float = 1f,
    height: Dp = STEPPER_HEIGHT,
    onStep: (delta: Int) -> Unit,
    onTapped: ((bpm: Int) -> Unit)? = null,
    onReset: () -> Unit,
) = Stepper(
    modifier = modifier,
    value = tempo.bpm.toString(),
    fontScale = fontScale,
    height = height,
    isDefault = tempo.isDefault,
    decreaseIcon = painterResource(Res.drawable.ic_subtract),
    decreaseLabel = stringResource(Res.string.song_details_tempo_decrease),
    canDecrease = tempo.bpm > MetronomePattern.BPM_RANGE.first,
    onDecrease = { onStep(-1) },
    increaseIcon = painterResource(Res.drawable.ic_add),
    increaseLabel = stringResource(Res.string.song_details_tempo_increase),
    canIncrease = tempo.bpm < MetronomePattern.BPM_RANGE.last,
    onIncrease = { onStep(1) },
    resetLabel = stringResource(Res.string.song_details_tempo_reset),
    onReset = onReset,
    repeatsOnHold = true,
    trailing = onTapped?.let { onTempo -> { TapTempoSegment(fontScale = fontScale, onTempo = onTempo) } },
)

/**
 * Tap tempo as the last segment of the tempo stepper's own pill, see [TempoStepper]. It is as tall as the pill and
 * takes the press over its whole padding, so that the segment and the stepper's buttons are hit the same way.
 */
@Composable
private fun TapTempoSegment(
    fontScale: Float,
    onTempo: (bpm: Int) -> Unit,
) {
    val tapTempo = remember { TapTempo() }
    val description = stringResource(Res.string.metronome_tap_description)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(role = Role.Button) { tapTempo.tap()?.let(onTempo) }
            .padding(horizontal = TAP_SEGMENT_PADDING)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.metronome_tap),
            style = MaterialTheme.typography.labelLarge.scaled(fontScale),
            // One word, and at a large text size in a narrow column there is not always room for it: it is cut off
            // rather than broken into two lines, which read as two buttons.
            maxLines = 1,
        )
    }
}

/** What the Tap segment keeps at its ends, a little less than the pill's buttons, since its label is wider than an icon. */
private val TAP_SEGMENT_PADDING = 10.dp

/**
 * The time signature as bars to pick from: the common ones as chips, and the two steppers under them for every other
 * bar. Shared by the Metronome tab, where it sets the tab's own signature, and by the "Song defaults" sheet that writes
 * a song's `{time}`, so that a bar is picked the same way wherever it is picked.
 *
 * @param horizontalPadding Where the chips start: the tab's own margin by default, nothing in a form that already pads
 * its fields. The stepper rows start a little before the chips, as a menu row's label does before a chip's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimeSignaturePicker(
    modifier: Modifier = Modifier,
    timeSignature: TimeSignature,
    horizontalPadding: Dp = 16.dp,
    onChange: (TimeSignature) -> Unit,
) = Column(modifier = modifier) {
    val rowPadding = (horizontalPadding - TIME_SIGNATURE_ROW_INSET).coerceAtLeast(0.dp)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        COMMON_TIME_SIGNATURES.forEach { common ->
            FilterChip(
                selected = common == timeSignature,
                onClick = { onChange(common) },
                label = { Text(common.toString()) },
            )
        }
    }
    MenuStepperRow(label = stringResource(Res.string.metronome_beats_per_bar), horizontalPadding = rowPadding) {
        Stepper(
            value = timeSignature.beats.toString(),
            isDefault = true,
            decreaseIcon = painterResource(Res.drawable.ic_subtract),
            decreaseLabel = stringResource(Res.string.metronome_beats_decrease),
            canDecrease = timeSignature.beats > TimeSignature.BEATS_RANGE.first,
            onDecrease = { onChange(timeSignature.copy(beats = timeSignature.beats - 1)) },
            increaseIcon = painterResource(Res.drawable.ic_add),
            increaseLabel = stringResource(Res.string.metronome_beats_increase),
            canIncrease = timeSignature.beats < TimeSignature.BEATS_RANGE.last,
            onIncrease = { onChange(timeSignature.copy(beats = timeSignature.beats + 1)) },
            resetLabel = null,
            onReset = null,
        )
    }
    val unitIndex = TimeSignature.UNITS.indexOf(timeSignature.unit)
    MenuStepperRow(label = stringResource(Res.string.metronome_beat_unit), horizontalPadding = rowPadding) {
        Stepper(
            value = timeSignature.unit.toString(),
            isDefault = true,
            decreaseIcon = painterResource(Res.drawable.ic_subtract),
            decreaseLabel = stringResource(Res.string.metronome_beat_unit_decrease),
            canDecrease = unitIndex > 0,
            onDecrease = { onChange(timeSignature.copy(unit = TimeSignature.UNITS[unitIndex - 1])) },
            increaseIcon = painterResource(Res.drawable.ic_add),
            increaseLabel = stringResource(Res.string.metronome_beat_unit_increase),
            canIncrease = unitIndex < TimeSignature.UNITS.lastIndex,
            onIncrease = { onChange(timeSignature.copy(unit = TimeSignature.UNITS[unitIndex + 1])) },
            resetLabel = null,
            onReset = null,
        )
    }
}

/** How much further out than the chips the stepper rows of [TimeSignaturePicker] start, see its `horizontalPadding`. */
private val TIME_SIGNATURE_ROW_INSET = 4.dp

/** The bars most songs are in, offered as chips so that the two steppers are only needed for the rest. */
private val COMMON_TIME_SIGNATURES = listOf(
    TimeSignature(2, 4),
    TimeSignature(3, 4),
    TimeSignature(4, 4),
    TimeSignature(6, 8),
    TimeSignature(7, 8),
    TimeSignature(12, 8),
)

/**
 * Taps a tempo in: from the second tap on, every tap sets the tempo the taps make ([TapTempo]). The series lives as
 * long as the button does, and starts again after a pause.
 *
 * It is drawn at exactly the [height] of the stepper it stands next to, since Material's own is a minimum that the
 * label outgrows at a large text size and never reaches at a small one, which left the button taller or shorter than
 * the steppers beside it at every size but the one the two happened to agree on. The label is centered in that height
 * rather than padded into it, so a large system font size still has the whole of it to be laid out in.
 */
@Composable
internal fun TapTempoButton(
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    height: Dp = STEPPER_HEIGHT,
    onTempo: (bpm: Int) -> Unit,
) {
    val tapTempo = remember { TapTempo() }
    val description = stringResource(Res.string.metronome_tap_description)
    TextButton(
        modifier = modifier
            .height(height)
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = TAP_HORIZONTAL_PADDING),
        onClick = { tapTempo.tap()?.let(onTempo) },
    ) {
        Text(
            text = stringResource(Res.string.metronome_tap),
            style = LocalTextStyle.current.scaled(fontScale),
            // One word, and at a large text size in a narrow column there is not always room for it: it is cut off
            // rather than broken into two lines, which read as two buttons.
            maxLines = 1,
        )
    }
}

/** Material's own text button padding, kept here so that it can be scaled with the label inside it. */
private val TAP_HORIZONTAL_PADDING = 12.dp

/**
 * The Italian tempo marking a tempo falls under, shown under the Metronome tab's tempo. Not translated: it is notation,
 * written the same in every language a score is read in.
 */
internal fun tempoMarking(bpm: Int) = TEMPO_MARKINGS.last { bpm >= it.first }.second

private val TEMPO_MARKINGS = listOf(
    0 to "Grave",
    40 to "Largo",
    55 to "Adagio",
    70 to "Andante",
    90 to "Moderato",
    110 to "Allegro",
    140 to "Vivace",
    170 to "Presto",
    200 to "Prestissimo",
)
