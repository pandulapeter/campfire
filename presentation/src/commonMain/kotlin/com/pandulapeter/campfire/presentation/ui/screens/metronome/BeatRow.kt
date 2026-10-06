/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.metronome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_beat_description
import com.pandulapeter.campfire.presentation.resources.metronome_level_accent
import com.pandulapeter.campfire.presentation.resources.metronome_level_muted
import com.pandulapeter.campfire.presentation.resources.metronome_level_normal
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * One block per beat of the bar, as tall as the beat is loud (a muted one only an outline) and resting in a fainter shade
 * of the second accent color, lit in the full color as each is heard - from the heard-time beats alone, so the flash agrees with the ear - and fading down to the
 * next. A tap moves a beat on from accent to plain to muted and round again, which is the whole of the accent editor.
 *
 * A bar that changes length - another time signature, or a song paged to in another one - is narrated rather than
 * swapped: the blocks it gains grow in at its end and the ones it loses shrink away there, one after another, while the
 * blocks it keeps narrow or widen to make room.
 *
 * It is the same row and the same editor wherever it is drawn, the song details panel's included: the accents belong to
 * the bar rather than to the screen they are tapped on, so the two cannot disagree about what is accented in 4/4.
 *
 * @param blockHeight How tall an accent is drawn, and how tall a column each beat is tapped in.
 */
@Composable
internal fun BeatRow(
    modifier: Modifier = Modifier,
    beatLevels: List<BeatLevel>,
    beats: Flow<MetronomeBeat>,
    isPlaying: Boolean,
    isFlashEnabled: Boolean,
    blockHeight: Dp,
    blockGap: Dp,
    onBeatLevelsChanged: (List<BeatLevel>) -> Unit,
) {
    var litBeat by remember { mutableIntStateOf(-1) }
    val flash = remember { Animatable(0f) }
    // Not gated on isPlaying: the engine only emits beats while it plays, and a click started on an output with little
    // latency is heard before the composition has caught up with it playing, which would leave its first beat dark.
    val shouldFlash by rememberUpdatedState(isFlashEnabled)
    LaunchedEffect(beats) {
        // The latest beat cuts the fade of the one before it short, so that a fade longer than a beat of a fast tempo
        // never leaves the row behind the click.
        beats.filter { !it.isSubdivision }.collectLatest { beat ->
            if (shouldFlash) {
                litBeat = beat.beatIndex
                flash.snapTo(1f)
                flash.animateTo(0f)
            }
        }
    }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            flash.snapTo(0f)
            litBeat = -1
        }
    }
    // The bar is drawn as a count that slides between the old and the new number of beats, so a bar that changes length
    // grows or loses its blocks one after another while the ones it keeps narrow or widen to make room for them. A beat
    // on its way out keeps the level it was drawn with, since the bar it belonged to is no longer there to say it.
    val animatedCount by animateFloatAsState(beatLevels.size.toFloat())
    val shownLevels = remember { ArrayList(beatLevels) }
    beatLevels.forEachIndexed { index, level ->
        if (index < shownLevels.size) shownLevels[index] = level else shownLevels.add(level)
    }
    val slotCount = maxOf(beatLevels.size, ceil(animatedCount).toInt())
    Layout(
        modifier = modifier.fillMaxWidth().heightIn(min = blockHeight),
        content = {
            for (index in 0 until slotCount) {
                val isLeaving = index >= beatLevels.size
                val level = shownLevels[index]
                BeatBlock(
                    modifier = Modifier.graphicsLayer { alpha = presenceOf(index, animatedCount) },
                    index = index,
                    level = level,
                    maxHeight = blockHeight,
                    isLeaving = isLeaving,
                    flash = { if (litBeat == index) flash.value else 0f },
                    onClick = { onBeatLevelsChanged(beatLevels.toMutableList().apply { set(index, level.next()) }) },
                )
            }
        },
    ) { measurables, constraints ->
        // A custom layout rather than weighted children, since a weight of zero is not allowed and the gap in front of a
        // block has to grow and shrink with it: an arriving block starts as nothing, gap included, and the others only
        // give up the room it takes.
        val presences = measurables.indices.map { presenceOf(it, animatedCount) }
        val gap = blockGap.toPx()
        val gaps = gap * presences.drop(1).sum()
        val unit = (constraints.maxWidth - gaps).coerceAtLeast(0f) / presences.sum().coerceAtLeast(1f)
        val height = maxOf(constraints.minHeight, blockHeight.roundToPx())
        var x = 0f
        val placed = measurables.mapIndexed { index, measurable ->
            if (index > 0) x += gap * presences[index]
            val width = unit * presences[index]
            val placeable = measurable.measure(Constraints.fixed((x + width).roundToInt() - x.roundToInt(), height))
            (placeable to x.roundToInt()).also { x += width }
        }
        layout(constraints.maxWidth, height) {
            placed.forEach { (placeable, offset) -> placeable.placeRelative(offset, height - placeable.height) }
        }
    }
}

/** How much of the beat at [index] is in the bar while its length animates towards a new one: 0 gone, 1 wholly there. */
private fun presenceOf(index: Int, animatedCount: Float) = (animatedCount - index).coerceIn(0f, 1f)

@Composable
private fun BeatBlock(
    modifier: Modifier = Modifier,
    index: Int,
    level: BeatLevel,
    maxHeight: Dp,
    isLeaving: Boolean,
    flash: () -> Float,
    onClick: () -> Unit,
) {
    val height by animateDpAsState(
        when (level) {
            BeatLevel.ACCENT -> maxHeight
            BeatLevel.NORMAL -> maxHeight * 0.6f
            BeatLevel.MUTED -> maxHeight * 0.3f
        }
    )
    // The blocks at rest are fainter shades of the color they flash in rather than colors of their own: only the app's
    // own palette has a second accent, every other one hands the primary color out as it, and an accent block resting
    // in the primary color would have nothing to flash into there.
    val flashColor = LocalSecondAccentColor.current
    val baseColor by animateColorAsState(
        when (level) {
            BeatLevel.ACCENT -> flashColor.copy(alpha = ACCENT_REST_ALPHA)
            BeatLevel.NORMAL -> flashColor.copy(alpha = NORMAL_REST_ALPHA)
            BeatLevel.MUTED -> Color.Transparent
        }
    )
    val outlineColor = MaterialTheme.colorScheme.outline
    val description = stringResource(
        Res.string.metronome_beat_description,
        index + 1,
        stringResource(
            when (level) {
                BeatLevel.ACCENT -> Res.string.metronome_level_accent
                BeatLevel.NORMAL -> Res.string.metronome_level_normal
                BeatLevel.MUTED -> Res.string.metronome_level_muted
            }
        ),
    )
    val shape = RoundedCornerShape(minOf(8.dp, maxHeight / 6))
    // The press belongs to the whole column rather than to the block drawn in it: a muted beat is a sliver less than a
    // third as tall as an accent, and in the song details panel's small row it would be a few millimeters of a target.
    // The ripple is the block's, though, so that what lights up under the finger is the beat that changes.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(maxHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isLeaving,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (isLeaving) Modifier.clearAndSetSemantics {} else Modifier.semantics { contentDescription = description }),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .indication(interactionSource, ripple())
                .then(if (level == BeatLevel.MUTED) Modifier.border(1.dp, outlineColor, shape) else Modifier)
                // Drawn rather than composed from the flash, so that a beat repaints the block instead of recomposing the row.
                .drawBehind { drawRect(lerp(baseColor, flashColor, flash())) },
        )
    }
}

private const val ACCENT_REST_ALPHA = 0.5f
private const val NORMAL_REST_ALPHA = 0.22f
