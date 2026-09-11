/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_check
import org.jetbrains.compose.resources.painterResource

/**
 * A single choice between colors, offered as the colors themselves: a disc for each, the selected one ringed. Their
 * names are only in the semantics, since a label under every disc would say less than the disc does - except for the
 * one or two whose point is not the color they show, which carry an icon that says what they are.
 *
 * They wrap rather than scroll, because a row of colors that has to be scrolled hides some of the options behind a
 * gesture while there is no order along which one could be looked for.
 */
@Composable
internal fun <T> ColorChoice(
    modifier: Modifier = Modifier,
    options: List<ColorChoiceOption<T>>,
    selected: T?,
    onSelected: (T) -> Unit,
) = FlowRow(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP),
    verticalArrangement = Arrangement.spacedBy(SWATCH_GAP),
) {
    options.forEach { option ->
        ColorChoiceSwatch(
            option = option,
            isSelected = option.value == selected,
            onClick = { onSelected(option.value) },
        )
    }
}

/**
 * One disc of a [ColorChoice], inside the ring that marks it as the selected one.
 *
 * The ring is what says "this is the one in use", and the check inside only seconds it: with an icon on some of the
 * discs, a mark in the middle is first read as *what this color is* rather than as a selection, so the selection has
 * to be somewhere an icon can never be. It is drawn in the option's own color, which for the selected one is the
 * color the whole app is wearing.
 *
 * Its own composable rather than part of the loop above because the row it sits in is a `FlowRow`, and the
 * `AnimatedVisibility` of the check would resolve to the `RowScope` overload inside it.
 */
@Composable
private fun <T> ColorChoiceSwatch(
    modifier: Modifier = Modifier,
    option: ColorChoiceOption<T>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    // Faded in and out rather than grown, and always laid out, so that the discs never move as the selection does.
    val ringAlpha by animateFloatAsState(targetValue = if (isSelected) 1f else 0f)
    Box(
        modifier = modifier
            .size(SWATCH_SIZE + (SWATCH_RING_GAP + SWATCH_RING_WIDTH) * 2)
            .border(SWATCH_RING_WIDTH, option.color.copy(alpha = ringAlpha), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(SWATCH_SIZE).semantics { contentDescription = option.label },
            selected = isSelected,
            onClick = onClick,
            shape = CircleShape,
            color = option.color,
            contentColor = option.contentColor,
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                    )
                }
                // The icon steps aside for the check rather than sharing the disc with it: a disc this size has room
                // for one mark, and the ring outside is already saying that this is the selected one.
                option.icon?.let { icon ->
                    AnimatedVisibility(
                        visible = !isSelected,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * What one disc of a [ColorChoice] stands for and is drawn in.
 *
 * @param color What the disc is filled with, and [contentColor] what the check on it is drawn in - the two roles of
 *   the same palette, so that the check stays legible on every color.
 * @param label What this color is called, which is only ever read out rather than shown.
 * @param icon Drawn on the disc while the option is not selected, for an option whose color is not the whole story.
 *   Null for the colors that are only themselves, which is most of them.
 */
internal data class ColorChoiceOption<T>(
    val value: T,
    val color: Color,
    val contentColor: Color,
    val label: String,
    val icon: Painter? = null,
)

/** Large enough to be the whole touch target, so the discs need no padding of their own to be tappable. */
private val SWATCH_SIZE = 48.dp

private val SWATCH_RING_WIDTH = 2.dp

/** The clear space between a disc and its ring, without which the ring would read as a rim of the disc itself. */
private val SWATCH_RING_GAP = 2.dp

private val SWATCH_GAP = 8.dp
