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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A single choice between a handful of [options], rendered as a segmented button row.
 *
 * @param isEnabled False where the choice has no effect right now, in which case the row still shows which option
 *   is selected rather than disappearing: what it would go back to once it matters again is worth seeing.
 * @param isInline True where the choice shares a row with other controls, as in the editor's bar: it keeps a
 *   narrower gap from them than from the edges of a screen, and a selected segment has no check mark - its fill
 *   already marks it, and the mark's 26dp is most of what a label has there on a phone.
 */
@Composable
internal fun <T> SegmentedChoice(
    modifier: Modifier = Modifier,
    options: List<Pair<T, String>>,
    selected: T?,
    isEnabled: Boolean = true,
    isInline: Boolean = false,
    onSelected: (T) -> Unit,
) = SingleChoiceSegmentedButtonRow(
    modifier = modifier.fillMaxWidth().padding(horizontal = if (isInline) 8.dp else 16.dp)
) {
    options.forEachIndexed { index, (value, label) ->
        SegmentedButton(
            selected = value == selected,
            onClick = { onSelected(value) },
            enabled = isEnabled,
            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            icon = if (isInline) ({}) else ({ SegmentedButtonDefaults.Icon(value == selected) }),
            label = {
                Text(
                    // Material measures the label at the whole width of the segment and then places it after the check
                    // mark of a selected one, so a label as wide as the segment ran on under its shape instead of being
                    // ellipsized. The check mark's room is taken off the width the label is offered.
                    modifier = if (value == selected && !isInline) Modifier.withoutCheckMarkWidth() else Modifier,
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}

/**
 * Measures the content with the width of a selected segment's check mark and the gap after it taken off: 18dp
 * (`SegmentedButtonDefaults.IconSize`) and 8dp, which is Material's own spacing and not public.
 */
private fun Modifier.withoutCheckMarkWidth() = layout { measurable, constraints ->
    val reserved = (SegmentedButtonDefaults.IconSize + CHECK_MARK_SPACING).roundToPx()
    val placeable = measurable.measure(
        if (constraints.hasBoundedWidth) {
            constraints.copy(minWidth = 0, maxWidth = (constraints.maxWidth - reserved).coerceAtLeast(0))
        } else {
            constraints
        },
    )
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private val CHECK_MARK_SPACING = 8.dp
