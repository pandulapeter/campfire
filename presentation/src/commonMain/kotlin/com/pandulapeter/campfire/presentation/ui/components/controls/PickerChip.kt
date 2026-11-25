/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A choice made in a sheet, named by what is chosen now and opening the sheet that changes it: a tonal pill like the
 * steppers and the instrument pill, [PICKER_CHIP_HEIGHT] tall, its [text] in [style] and no chevron, as the instrument
 * pill has none: the filled pill naming a value is what says it is a choice. The text crossfades as it changes and the pill resizes with it, and a text too long for the row is cut short
 * rather than wrapped. [onClickLabel] is what a screen reader says a tap does.
 */
@Composable
internal fun PickerChip(
    modifier: Modifier = Modifier,
    text: String,
    onClickLabel: String,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) = Surface(
    // Dimmed the way the list items of Settings are while their section is switched off.
    modifier = modifier.height(PICKER_CHIP_HEIGHT).alpha(if (isEnabled) 1f else 0.5f),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    Row(
        modifier = Modifier
            .clickable(enabled = isEnabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
            .padding(horizontal = PICKER_CHIP_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            modifier = Modifier.weight(1f, fill = false),
            targetState = text,
            transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
        ) { shownText ->
            Text(
                text = shownText,
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How tall a [PickerChip] is, which the instrument pill among them takes too (the Preferences' Chords page). */
internal val PICKER_CHIP_HEIGHT = 40.dp

private val PICKER_CHIP_HORIZONTAL_PADDING = 16.dp
