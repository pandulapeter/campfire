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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * @param horizontalInset Room kept at either end inside the row, on top of the list item's own, so that a row that
 *   reaches past the content it sits in - the checklist of a dialog, whose rows light up to its edges - can still line
 *   its checkbox up with that content.
 * @param isEnabled False for a box that cannot be changed from where the list was opened, which stays in its list,
 *   dimmed, showing what it holds.
 * @param coverArtUrl The song's cover, drawn as a small thumbnail at the end of the row, after the text rather than
 *   before it, where the checkbox already is.
 */
@Composable
internal fun CheckboxListItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    isChecked: Boolean,
    isEnabled: Boolean = true,
    horizontalInset: Dp = 0.dp,
    coverArtUrl: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) = ListItem(
    modifier = modifier
        .toggleable(value = isChecked, enabled = isEnabled, role = Role.Checkbox, onValueChange = onCheckedChange)
        .alpha(if (isEnabled) 1f else 0.5f)
        .padding(horizontal = horizontalInset),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = description?.let { { Text(it) } },
    leadingContent = { Checkbox(checked = isChecked, enabled = isEnabled, onCheckedChange = null) },
    trailingContent = coverArtUrl?.let {
        {
            CoverArtImage(
                modifier = Modifier.size(CHECKBOX_LIST_ITEM_COVER_SIZE),
                url = it,
                shape = MaterialTheme.shapes.small,
            )
        }
    },
)

/** A checklist row's cover as large as Material's leading avatar, which fits a row that is only a title. */
private val CHECKBOX_LIST_ITEM_COVER_SIZE = 40.dp
