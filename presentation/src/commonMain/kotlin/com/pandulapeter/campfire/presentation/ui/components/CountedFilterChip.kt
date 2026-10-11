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

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One value of a filter group: what it is called, how many songs it still leaves, and whether it is on. The count is
 * part of the chip rather than a line under the group, since the number is what tells a tag worth picking from one
 * that would leave a single song on screen.
 *
 * A value the other group has counted down to nothing is disabled rather than left out (see `ScreenData.tags`): the
 * chips hold still while the other group changes, and picking it would only empty the list. One that is already on
 * stays enabled whatever its count, since a filter that is on has to be possible to turn off.
 *
 * It is a [SelectableChip], for the reason that one exists.
 *
 * @param leadingIcon What the chip is, the mark a tag or a language carries wherever it is drawn: the song picker lists
 *   the two in one row, where nothing else tells them apart, and the song filters give them the same marks, a tag's
 *   being where its color is painted.
 * @param leadingIconFill The color a tag was given, painted inside its mark ([TagIcon]); unspecified for none.
 */
@Composable
internal fun CountedFilterChip(
    modifier: Modifier = Modifier,
    label: String,
    songCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    leadingIcon: Painter? = null,
    leadingIconFill: Color = Color.Unspecified,
) {
    val isEnabled = songCount > 0 || isSelected
    val colors = FilterChipDefaults.filterChipColors()
    SelectableChip(
        modifier = modifier,
        isSelected = isSelected,
        isEnabled = isEnabled,
        role = Role.Checkbox,
        onClick = onClick,
    ) { contentColor ->
        if (leadingIcon != null) {
            Crossfade(targetState = isSelected) { isChecked ->
                TagIcon(
                    modifier = Modifier.padding(end = CHIP_ICON_GAP).size(FilterChipDefaults.IconSize),
                    painter = leadingIcon,
                    fill = if (isEnabled || leadingIconFill.isUnspecified) leadingIconFill else leadingIconFill.copy(alpha = DISABLED_FILL_ALPHA),
                    tint = animateColorAsState(
                        when {
                            !isEnabled -> colors.disabledLeadingIconColor
                            isChecked -> colors.selectedLeadingIconColor
                            else -> colors.leadingIconColor
                        }
                    ).value,
                )
            }
        }
        Text(
            modifier = Modifier.widthIn(max = MAX_TAG_WIDTH),
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            modifier = Modifier.padding(
                start = TAG_GAP,
                top = 2.dp,
            ),
            text = songCount.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else colors.disabledLabelColor,
        )
    }
}

private val MAX_TAG_WIDTH = 160.dp

private val CHIP_ICON_GAP = 8.dp

/** What Material dims a disabled chip's content to, which a color inside its mark has to follow too. */
private const val DISABLED_FILL_ALPHA = 0.38f
