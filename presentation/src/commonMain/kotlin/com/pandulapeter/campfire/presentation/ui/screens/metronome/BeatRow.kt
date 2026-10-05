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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * One block per beat of the bar, as tall as the beat is loud (a muted one only an outline) and resting in a fainter shade
 * of the second accent color, lit in the full color as each is heard - from the heard-time beats alone, so the flash agrees with the ear - and fading down to the
 * next. A tap moves a beat on from accent to plain to muted and round again, which is the whole of the accent editor.
 */
@Composable
internal fun BeatRow(
    modifier: Modifier = Modifier,
    beatLevels: List<BeatLevel>,
    beats: Flow<MetronomeBeat>,
    isPlaying: Boolean,
    isFlashEnabled: Boolean,
    onBeatLevelsChanged: (List<BeatLevel>) -> Unit,
) {
    var litBeat by remember { mutableIntStateOf(-1) }
    val flash = remember { Animatable(0f) }
    val shouldFlash by rememberUpdatedState(isFlashEnabled && isPlaying)
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
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = MAX_BLOCK_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(BLOCK_GAP),
        verticalAlignment = Alignment.Bottom,
    ) {
        beatLevels.forEachIndexed { index, level ->
            BeatBlock(
                modifier = Modifier.weight(1f),
                index = index,
                level = level,
                flash = { if (litBeat == index) flash.value else 0f },
                onClick = { onBeatLevelsChanged(beatLevels.toMutableList().apply { set(index, level.next()) }) },
            )
        }
    }
}

@Composable
private fun BeatBlock(
    modifier: Modifier = Modifier,
    index: Int,
    level: BeatLevel,
    flash: () -> Float,
    onClick: () -> Unit,
) {
    val height by animateDpAsState(
        when (level) {
            BeatLevel.ACCENT -> MAX_BLOCK_HEIGHT
            BeatLevel.NORMAL -> MAX_BLOCK_HEIGHT * 0.6f
            BeatLevel.MUTED -> MAX_BLOCK_HEIGHT * 0.3f
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
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .then(if (level == BeatLevel.MUTED) Modifier.border(1.dp, outlineColor, shape) else Modifier)
            // Drawn rather than composed from the flash, so that a beat repaints the block instead of recomposing the row.
            .drawBehind { drawRect(lerp(baseColor, flashColor, flash())) }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    )
}

private val MAX_BLOCK_HEIGHT = 56.dp
private val BLOCK_GAP = 6.dp
private const val ACCENT_REST_ALPHA = 0.5f
private const val NORMAL_REST_ALPHA = 0.22f
