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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_metronome_body
import com.pandulapeter.campfire.presentation.resources.ic_metronome_pendulum
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.cos

/**
 * The metronome mark following the click as [beat] says: its pendulum where the swing puts it, and the whole mark
 * growing and taking the primary color on every beat as the pulse fades. The body and the pendulum are two drawables,
 * laid over each other, so that only the pendulum turns, about the pivot the body's crossbar hides.
 *
 * @param tint The mark's color between beats, which the pulse leans towards the primary color.
 */
@Composable
internal fun MetronomeIcon(
    modifier: Modifier = Modifier,
    beat: MetronomeIconBeat,
    contentDescription: String?,
    tint: Color = LocalContentColor.current,
) {
    val body = painterResource(Res.drawable.ic_metronome_body)
    val pendulum = painterResource(Res.drawable.ic_metronome_pendulum)
    val pulseColor = MaterialTheme.colorScheme.primary
    // Read while drawing rather than while composing, so that a pulse repaints the mark instead of recomposing it on
    // every frame. The two layers stand in for Material's Icon, which takes the tint as a composition parameter.
    val colorFilter = { ColorFilter.tint(lerp(tint, pulseColor, beat.pulse.coerceIn(0f, 1f))) }
    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = 1f + beat.pulse * PULSE_SCALE
                scaleX = scale
                scaleY = scale
            }
            .size(ICON_SIZE)
            .then(
                if (contentDescription == null) Modifier else Modifier.semantics {
                    this.contentDescription = contentDescription
                    role = Role.Image
                },
            ),
    ) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .drawBehind { with(body) { draw(size = size, colorFilter = colorFilter()) } },
        )
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = -beat.swing * PENDULUM_ARC
                    transformOrigin = PENDULUM_PIVOT
                }
                .drawBehind { with(pendulum) { draw(size = size, colorFilter = colorFilter()) } },
        )
    }
}

/**
 * Where a [MetronomeIcon] is in the beat, see [rememberMetronomeIconBeat].
 *
 * @property swing Where the pendulum is: 0 is the mark as it is drawn everywhere else, leaning right, and 1 is its
 * mirror image, leaning left.
 * @property pulse How much of a beat's pulse is left: 1 just after an accent, 0.6 just after any other beat, 0 once it
 * has faded.
 */
@Stable
internal class MetronomeIconBeat(
    private val swingState: Animatable<Float, *>,
    private val pulseState: Animatable<Float, *>,
) {
    val swing get() = swingState.value
    val pulse get() = pulseState.value
}

/**
 * Drives a [MetronomeIcon] from the heard beats. The pendulum is at one end of its arc on every beat and swings to the
 * other end over the length of one, the way a mechanical metronome ticks at the turn, so that the eye and the ear agree.
 * Each beat starts the swing that ends on the next one, from wherever the pendulum is, so a beat heard a frame late or a
 * tempo changed mid-swing is caught up with within one beat rather than drifting; and it cuts the fade of the pulse
 * before it short, so that a fade longer than a beat of a fast tempo never leaves the pulse behind the click. Both only
 * move while the visual beat is wanted ([isEnabled], the "Animate on the beat" switch the other visual beats follow);
 * otherwise, and once the click stops, the pendulum goes back to where the mark is drawn at rest.
 *
 * The beats are not gated on the composed playback, as the beat row's are not: the first beat can be heard before the
 * composition knows the click plays, and the engine emits no beat while it does not. For the same reason the tempo is
 * read from [playback] as the beat arrives, since the swing it starts needs its length.
 */
@Composable
internal fun rememberMetronomeIconBeat(
    playback: StateFlow<MetronomePlayback>,
    beats: Flow<MetronomeBeat>,
    isEnabled: Boolean,
): MetronomeIconBeat {
    val swing = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }
    val isPlaying = playback.collectAsStateWithLifecycle().value is MetronomePlayback.Playing
    val shouldMove by rememberUpdatedState(isEnabled)
    LaunchedEffect(playback, beats) {
        beats.filter { !it.isSubdivision }.collectLatest { beat ->
            val bpm = (playback.value as? MetronomePlayback.Playing)?.pattern?.bpm
            if (shouldMove) coroutineScope {
                launch {
                    pulse.snapTo(if (beat.level == BeatLevel.ACCENT) 1f else 0.6f)
                    pulse.animateTo(0f)
                }
                if (bpm != null) {
                    swing.animateTo(
                        targetValue = if (swing.targetValue == 1f) 0f else 1f,
                        animationSpec = tween(durationMillis = MILLIS_PER_MINUTE / bpm, easing = PendulumEasing),
                    )
                }
            }
        }
    }
    LaunchedEffect(isPlaying, isEnabled) {
        if (!isPlaying || !isEnabled) {
            pulse.snapTo(0f)
            swing.animateTo(0f)
        }
    }
    return remember(swing, pulse) { MetronomeIconBeat(swingState = swing, pulseState = pulse) }
}

/** The size Material's `Icon` gives a 24-unit drawable, which both of the mark's drawables are. */
private val ICON_SIZE = 24.dp

/** How much larger the mark is at the height of an accent's pulse. */
private const val PULSE_SCALE = 0.25f

/** A pendulum's own motion, slowest at the ends of the arc and fastest through the middle. */
private val PendulumEasing = Easing { fraction -> (1f - cos(PI.toFloat() * fraction)) / 2f }

private const val MILLIS_PER_MINUTE = 60_000

/**
 * The pendulum's arc in degrees: twice the lean of the drawn arm, from its pivot to its tip (5.3 units across for 10.5
 * up), so that both ends of the swing are the drawn mark and its mirror image.
 */
private const val PENDULUM_ARC = 53.6f

/** The pivot of the pendulum's arm on the drawables' 24-unit grid, under the body's crossbar. */
private val PENDULUM_PIVOT = TransformOrigin(pivotFractionX = 11.975f / 24f, pivotFractionY = 17.24f / 24f)
