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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_review_for_all

/**
 * The heading of a group of the review with its count, and where the group has boxes, a box at its end ticking or
 * unticking every row the group shows; the names already taken have the choice for all of them under it.
 *
 * @param boxState Null for a group without boxes.
 * @param isConflicts Whether this is the group of names already taken, which has the choice for all of them.
 * @param resolution What every row the group shows resolves to; null where they differ, shown as Mixed.
 */
@Composable
internal fun ImportReviewSectionHeader(
    modifier: Modifier = Modifier,
    title: String,
    count: Int,
    boxState: ToggleableState?,
    onBoxClick: () -> Unit,
    isConflicts: Boolean,
    resolution: ImportConflictResolution?,
    onResolutionSelected: (ImportConflictResolution) -> Unit,
) = Column(modifier = modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(
            modifier = Modifier.weight(1f),
            text = title,
            count = count,
        )
        if (boxState != null) {
            TriStateCheckbox(
                modifier = Modifier.padding(end = 12.dp),
                state = boxState,
                onClick = onBoxClick,
            )
        }
    }
    if (isConflicts) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(Res.string.import_review_for_all),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConflictResolutionDropdown(
                resolution = resolution,
                onResolutionSelected = onResolutionSelected,
            )
        }
    }
}
