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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_expand
import org.jetbrains.compose.resources.painterResource

/**
 * A choice made in a sheet, named by what is chosen now and opening the sheet that changes it: a tonal pill like the
 * steppers, [height] tall, its [text] in [style] and a chevron after it, which says that it is a choice rather than an
 * edit. The text crossfades as it changes and the pill resizes with it, and a text too long for the row is cut short
 * rather than wrapped. [onClickLabel] is what a screen reader says a tap does.
 */
@Composable
internal fun PickerChip(
    modifier: Modifier = Modifier,
    text: String,
    onClickLabel: String,
    height: Dp = 40.dp,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    chevronSize: Dp = 20.dp,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) = Surface(
    // Dimmed the way the list items of Settings are while their section is switched off.
    modifier = modifier.height(height).alpha(if (isEnabled) 1f else 0.5f),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    Row(
        modifier = Modifier
            .clickable(enabled = isEnabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
            .padding(start = PICKER_CHIP_START_PADDING, end = PICKER_CHIP_END_PADDING),
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
        Icon(
            modifier = Modifier.padding(start = PICKER_CHIP_CHEVRON_GAP).size(chevronSize),
            painter = painterResource(Res.drawable.ic_expand),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Before a picker chip's text, which is also how far a chip that starts a line of a song reaches out of its column to the
 * left, so that its text stands on the keyline the lyrics and the section titles do, as a section's pill does.
 */
internal val PICKER_CHIP_START_PADDING = 12.dp
private val PICKER_CHIP_END_PADDING = 8.dp
private val PICKER_CHIP_CHEVRON_GAP = 2.dp
