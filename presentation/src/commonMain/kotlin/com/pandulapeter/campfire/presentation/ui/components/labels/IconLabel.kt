/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.labels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_calendar
import com.pandulapeter.campfire.presentation.resources.ic_clock
import com.pandulapeter.campfire.presentation.resources.ic_metronome_body
import com.pandulapeter.campfire.presentation.resources.ic_metronome_pendulum
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * A fact about how something is played, marked with what it is a measure of: a song card's tempo and duration, and a
 * setlist's countdown. The mark is drawn as tall as a capital of the text, in its color,
 * so the two read as one word rather than as a button next to a label.
 *
 * @param style The type of the line it stands on, which the mark is sized by.
 */
@Composable
internal fun IconLabel(
    modifier: Modifier = Modifier,
    text: String,
    mark: IconLabelMark,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
) {
    val markSize = with(LocalDensity.current) { style.lineHeight.toDp() } - MARK_INSET
    Box(modifier = Modifier.padding(end = MARK_GAP).size(markSize)) {
        mark.drawables.forEach { drawable ->
            Icon(
                modifier = Modifier.matchParentSize(),
                painter = painterResource(drawable),
                contentDescription = null,
                tint = color,
            )
        }
    }
    Text(
        modifier = Modifier.weight(1f, fill = false),
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The marks an [IconLabel] is drawn with, each one or more drawables laid over each other: the metronome's comes in two
 * parts, so that the navigation item can swing the pendulum alone, and is drawn here at rest.
 */
internal enum class IconLabelMark(val drawables: List<DrawableResource>) {
    TEMPO(listOf(Res.drawable.ic_metronome_body, Res.drawable.ic_metronome_pendulum)),
    DURATION(listOf(Res.drawable.ic_clock)),
    DATE(listOf(Res.drawable.ic_calendar)),
}

/** How much shorter than the text's line the mark is, which leaves it about as tall as the text's capitals. */
private val MARK_INSET = 2.dp

/** The room between the mark and the text. */
private val MARK_GAP = 4.dp
