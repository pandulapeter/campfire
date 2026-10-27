/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.checklist

import com.pandulapeter.campfire.presentation.ui.components.coverArt.CoverArtImage
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.CrossfadedText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
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
) = CheckboxListItem(
    modifier = modifier,
    title = title,
    description = description,
    state = ToggleableState(isChecked),
    isEnabled = isEnabled,
    horizontalInset = horizontalInset,
    coverArtUrl = coverArtUrl,
    onClick = { onCheckedChange(!isChecked) },
)

/**
 * The same row with a box that can also be half ticked, for a list about several things at once - the songs ticked
 * in the selection mode - where a row can hold for some of them and not for the others. What a tap does is the
 * caller's, since the next state depends on where the row started.
 *
 * @param trailingContent A control of its own at the end of the row, which takes its own taps rather than ticking the
 *   box: the tag sheet's color of each tag. Not drawn where the row has a cover, which takes that place.
 * @param supportingControl A control of its own under the row's description, at the start of its text, which takes its
 *   own taps rather than ticking the box: the import review's choice for a name already taken, which leaves the title
 *   the row's whole width that a control at its end would take.
 */
@Composable
internal fun CheckboxListItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    state: ToggleableState,
    isEnabled: Boolean = true,
    horizontalInset: Dp = 0.dp,
    coverArtUrl: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingControl: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) = ListItem(
    modifier = modifier
        .triStateToggleable(state = state, enabled = isEnabled, role = Role.Checkbox, onClick = onClick)
        .alpha(if (isEnabled) 1f else 0.5f)
        .padding(horizontal = horizontalInset),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = if (description != null || supportingControl != null) {
        {
            Column {
                description?.let { CrossfadedText(text = it) }
                supportingControl?.invoke()
            }
        }
    } else {
        null
    },
    leadingContent = { TriStateCheckbox(state = state, enabled = isEnabled, onClick = null) },
    trailingContent = coverArtUrl?.let {
        {
            CoverArtImage(
                modifier = Modifier.size(CHECKBOX_LIST_ITEM_COVER_SIZE),
                url = it,
                shape = MaterialTheme.shapes.small,
            )
        }
    } ?: trailingContent,
)

/** A checklist row's cover as large as Material's leading avatar, which fits a row that is only a title. */
private val CHECKBOX_LIST_ITEM_COVER_SIZE = 40.dp
