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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.deselect_all
import com.pandulapeter.campfire.presentation.resources.select_all

/**
 * The heading of a group of the review, its count written into it, and where the group shows several rows with boxes,
 * a Select all / Deselect all button at the end of the heading's line acting on every one of them. The button is a
 * text button on the heading's line rather than a row with a box of its own, since a row laid out as the files' are
 * reads as one more file. In the group of the names already taken, the choice for all of them sits under the heading,
 * starting where its text does rather than where the rows' choices do, so that it reads as the heading's too.
 *
 * A group showing one row has neither: that row's own box and choice say the same.
 *
 * @param boxState Null where the group shows fewer than two rows with boxes.
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
    // Kept while the controls fold away, so that they leave as they were.
    var shownState by remember { mutableStateOf(boxState ?: ToggleableState.Off) }
    boxState?.let { shownState = it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(modifier = Modifier.weight(1f), text = title)
        // A joining batch can bring a group its second row, or a search leave it one.
        AnimatedVisibility(
            visible = boxState != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SelectAllButton(
                modifier = Modifier.padding(end = 4.dp),
                state = shownState,
                onClick = onBoxClick,
            )
        }
    }
    if (isConflicts) {
        AnimatedVisibility(
            visible = boxState != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                ConflictResolutionDropdown(
                    resolution = resolution,
                    onResolutionSelected = onResolutionSelected,
                )
            }
        }
    }
}

/** Reads Deselect all while every row shown is ticked, and Select all otherwise, crossfading between the two. */
@Composable
private fun SelectAllButton(
    modifier: Modifier,
    state: ToggleableState,
    onClick: () -> Unit,
) = TextButton(
    modifier = modifier,
    onClick = onClick,
) {
    AnimatedContent(
        targetState = if (state == ToggleableState.On) Res.string.deselect_all else Res.string.select_all,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) {
        Text(text = stringResource(it), maxLines = 1)
    }
}
