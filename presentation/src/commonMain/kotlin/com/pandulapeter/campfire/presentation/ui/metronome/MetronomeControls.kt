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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.metronome.api.TapTempo
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_metronome
import com.pandulapeter.campfire.presentation.resources.ic_stop
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.metronome_tap
import com.pandulapeter.campfire.presentation.resources.metronome_tap_description
import com.pandulapeter.campfire.presentation.resources.song_details_metronome_start
import com.pandulapeter.campfire.presentation.resources.song_details_metronome_stop
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_increase
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_reset
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.KEY_SEPARATOR
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.Stepper
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.painterResource

/**
 * The play and stop mark of the metronome panel's button, morphing from one into the other: the triangle's three corners
 * (one of them doubled) travel to the square's four, so the change reads as one shape becoming another rather than as
 * two icons swapping.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayStopMark(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    size: Dp = 24.dp,
    color: Color = LocalContentColor.current,
) {
    val progress by animateFloatAsState(if (isPlaying) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec())
    Canvas(modifier = modifier.size(size)) {
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

private fun lerp(start: Offset, stop: Offset, fraction: Float) = Offset(
    x = start.x + (stop.x - start.x) * fraction,
    y = start.y + (stop.y - start.y) * fraction,
)

/** Material's play triangle on its 24-unit grid, the tip doubled so that it can become two corners of the square. */
private val PLAY_CORNERS = listOf(Offset(8f, 5f), Offset(19f, 12f), Offset(19f, 12f), Offset(8f, 19f))
private val STOP_CORNERS = listOf(Offset(6f, 6f), Offset(18f, 6f), Offset(18f, 18f), Offset(6f, 18f))

/**
 * The song details screen's one-tap metronome: a metronome mark that becomes a stop mark while a click plays, and pulses
 * on every heard beat (in the second accent color, a little larger on an accent) unless the flash is off. Its content
 * description says the tempo it would start at, since that is all a screen reader user would otherwise not know.
 */
@Composable
internal fun MetronomeButton(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    bpm: Int,
    beats: Flow<MetronomeBeat>,
    isFlashEnabled: Boolean,
    onClick: () -> Unit,
) {
    val pulse = remember { Animatable(0f) }
    val shouldFlash by rememberUpdatedState(isFlashEnabled && isPlaying)
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
    val description = if (isPlaying) {
        stringResource(Res.string.song_details_metronome_stop)
    } else {
        "${stringResource(Res.string.song_details_metronome_start)} $KEY_SEPARATOR ${stringResource(Res.string.song_details_tempo, bpm.toString())}"
    }
    val accentColor = LocalSecondAccentColor.current
    val contentColor = LocalContentColor.current
    IconButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        AnimatedContent(
            modifier = Modifier.graphicsLayer {
                val scale = 1f + pulse.value * PULSE_SCALE
                scaleX = scale
                scaleY = scale
            },
            targetState = isPlaying,
            transitionSpec = { (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) },
        ) { playing ->
            Icon(
                painter = painterResource(if (playing) Res.drawable.ic_stop else Res.drawable.ic_metronome),
                contentDescription = description,
                tint = lerp(contentColor, accentColor, pulse.value.coerceIn(0f, 1f)),
            )
        }
    }
}

private const val PULSE_SCALE = 0.25f

/** [MetronomeButton] as an entry of the song details overflow menu, for a bar that has no room for the button. */
@Composable
internal fun metronomeAction(
    isPlaying: Boolean,
    bpm: Int,
    onClick: () -> Unit,
) = ActionsMenuItem(
    title = if (isPlaying) {
        stringResource(Res.string.song_details_metronome_stop)
    } else {
        "${stringResource(Res.string.song_details_metronome_start)} $KEY_SEPARATOR ${stringResource(Res.string.song_details_tempo, bpm.toString())}"
    },
    icon = painterResource(if (isPlaying) Res.drawable.ic_stop else Res.drawable.ic_metronome),
    key = METRONOME_ACTION_KEY,
    animateIconChange = true,
    onClick = onClick,
)

private const val METRONOME_ACTION_KEY = "metronome"

/**
 * The tempo stepper of a song, the transposition's twin gesture for gesture: highlighted while the song plays at a
 * tempo of its own here, and one tap on the value puts it back to the file's. Its buttons repeat while held, since a
 * tempo is often far from where it starts, and the tap button next to it is the quick way to a far value.
 */
@Composable
internal fun TempoControls(
    modifier: Modifier = Modifier,
    tempo: EffectiveTempo,
    onStep: (delta: Int) -> Unit,
    onTapped: (bpm: Int) -> Unit,
    onReset: () -> Unit,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
) {
    Stepper(
        value = tempo.bpm.toString(),
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
    )
    TapTempoButton(
        modifier = Modifier.padding(start = 4.dp),
        onTempo = onTapped,
    )
}

/**
 * Taps a tempo in: from the second tap on, every tap sets the tempo the taps make ([TapTempo]). The series lives as
 * long as the button does, and starts again after a pause.
 */
@Composable
internal fun TapTempoButton(
    modifier: Modifier = Modifier,
    onTempo: (bpm: Int) -> Unit,
) {
    val tapTempo = remember { TapTempo() }
    val description = stringResource(Res.string.metronome_tap_description)
    TextButton(
        modifier = modifier.semantics { contentDescription = description },
        onClick = { tapTempo.tap()?.let(onTempo) },
    ) {
        Text(text = stringResource(Res.string.metronome_tap))
    }
}

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
