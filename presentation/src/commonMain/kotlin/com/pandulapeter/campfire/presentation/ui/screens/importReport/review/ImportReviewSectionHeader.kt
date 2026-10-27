/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport.review

import com.pandulapeter.campfire.presentation.ui.screens.importReport.SectionTitle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.select_all

/**
 * The heading of a group of the review, its count written into it, and where the group has boxes, a Select all row
 * under it ticking or unticking every row the group shows. That row is laid out as the rows are, its box in the column
 * of theirs, so that it reads as one more row acting for all of them while the heading stays a heading; in the group of
 * the names already taken it also has the choice for all of them under its label, where the rows have theirs under their
 * text.
 *
 * @param boxState Null for a group without boxes.
 * @param isConflicts Whether this is the group of names already taken, which has the choice for all of them.
 * @param resolution What every row the group shows resolves to; null where they differ, shown as Mixed.
 */
@Composable
internal fun ImportReviewSectionHeader(
    modifier: Modifier = Modifier,
    title: String,
    boxState: ToggleableState?,
    onBoxClick: () -> Unit,
    isConflicts: Boolean,
    resolution: ImportConflictResolution?,
    onResolutionSelected: (ImportConflictResolution) -> Unit,
) = Column(modifier = modifier) {
    SectionTitle(text = title)
    // Kept while the row folds away, so that it leaves as it was rather than unticked.
    var shownState by remember { mutableStateOf(boxState ?: ToggleableState.Off) }
    boxState?.let { shownState = it }
    // A joining batch can bring a group its first box, or a search leave it none.
    AnimatedVisibility(
        visible = boxState != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        SelectAllRow(
            state = shownState,
            onClick = onBoxClick,
            choice = if (isConflicts) {
                {
                    ConflictResolutionDropdown(
                        resolution = resolution,
                        onResolutionSelected = onResolutionSelected,
                    )
                }
            } else {
                null
            },
        )
    }
}

/**
 * The box and the text at the places of a `CheckboxListItem`'s, on a line of the height of a touch target, and the
 * [choice] for every row on a line under it, starting where the text does, so that the label keeps the line's whole
 * width. The whole row, both lines, ticks the box; the choice takes its own taps.
 */
@Composable
private fun SelectAllRow(
    state: ToggleableState,
    onClick: () -> Unit,
    choice: (@Composable () -> Unit)?,
) = Column(
    modifier = Modifier.triStateToggleable(state = state, role = Role.Checkbox, onClick = onClick),
) {
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = state,
            onClick = null,
        )
        Text(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            text = stringResource(Res.string.select_all),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    choice?.let {
        Box(modifier = Modifier.padding(start = CHECKBOX_ZONE_WIDTH, end = 16.dp)) { it() }
    }
}
