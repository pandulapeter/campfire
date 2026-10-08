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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A Material filter chip drawn by hand rather than [androidx.compose.material3.FilterChip] itself, for the one thing the
 * chip gets wrong: its press and hover state layer is drawn around the label instead of around the chip, so only a band
 * hugging the text lights up inside the chip's own, which reads as a ripple inside a ripple. Everything the chip would
 * decide is still asked of `FilterChipDefaults`, so the colors, the border, the shape and the height are the ones
 * Material would have used; what is ours is the order of the modifiers. The indication is a node of its own
 * ([androidx.compose.foundation.indication]) sitting outside the padding and driven by the same interaction source as
 * the click, which is what puts the state layer on the chip's own bounds, and the clip above it is what rounds it -
 * the state layer is drawn as a plain rectangle and is bound by nothing else, which is what let it spill past the
 * rounded corners on the web.
 *
 * @param role [Role.Checkbox] for a chip that is one filter of several, [Role.RadioButton] for one choice of a group.
 * @param content The chip's label, handed the color it is drawn in.
 */
@Composable
internal fun SelectableChip(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    isEnabled: Boolean = true,
    role: Role,
    onClick: () -> Unit,
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    val colors = FilterChipDefaults.filterChipColors()
    val contentColor = animateColorAsState(
        when {
            !isEnabled -> colors.disabledLabelColor
            isSelected -> colors.selectedLabelColor
            else -> colors.labelColor
        }
    ).value
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(FilterChipDefaults.shape)
            .background(if (isSelected) colors.selectedContainerColor else colors.containerColor)
            .border(FilterChipDefaults.filterChipBorder(enabled = isEnabled, selected = isSelected), FilterChipDefaults.shape)
            .indication(interactionSource, ripple(color = contentColor))
            .selectable(
                selected = isSelected,
                enabled = isEnabled,
                interactionSource = interactionSource,
                indication = null,
                role = role,
                onClick = onClick,
            )
            .height(FilterChipDefaults.Height)
            .padding(horizontal = CHIP_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        content = { content(contentColor) },
    )
}

/** What a Material filter chip keeps between its border and its label, and between the check and the label. */
private val CHIP_PADDING = 16.dp
