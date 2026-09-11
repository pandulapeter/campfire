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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A single choice between a handful of [options], rendered as a segmented button row.
 *
 * @param isEnabled False where the choice has no effect right now, in which case the row still shows which option
 *   is selected rather than disappearing: what it would go back to once it matters again is worth seeing.
 */
@Composable
internal fun <T> SegmentedChoice(
    modifier: Modifier = Modifier,
    options: List<Pair<T, String>>,
    selected: T?,
    isEnabled: Boolean = true,
    onSelected: (T) -> Unit,
) = SingleChoiceSegmentedButtonRow(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)
) {
    options.forEachIndexed { index, (value, label) ->
        SegmentedButton(
            selected = value == selected,
            onClick = { onSelected(value) },
            enabled = isEnabled,
            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            label = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}
