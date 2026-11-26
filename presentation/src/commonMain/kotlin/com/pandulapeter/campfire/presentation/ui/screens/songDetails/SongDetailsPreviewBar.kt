/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportController
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportReportState

/**
 * [ImportPreviewBar] where the song details screen puts it: over the dock at the bottom of the pages, which leave it its
 * room, the card as far from the window's edges as from the dock under it, and the room reaching down over whatever of
 * the bottom inset nothing under it covers ([baseBottom]). Always under the song, beside the dock's side panel too,
 * never at the panel's head, which keeps its column of instrument card, diagrams, and tempo chip whole.
 */
@Composable
internal fun SongDetailsPreviewBar(
    importController: ImportController,
    importReportState: ImportReportState,
    importPreview: ImportPreviewState?,
    currentPage: Int,
    isAddingToReview: Boolean,
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
    baseBottom: Dp,
) = ImportPreviewBar(
    modifier = Modifier.fillMaxWidth(),
    roomPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + PREVIEW_CARD_MARGIN,
        top = PREVIEW_CARD_MARGIN,
        end = contentPadding.calculateEndPadding(layoutDirection) + PREVIEW_CARD_MARGIN,
        // No surface of the dock's stands behind the card, so it keeps the same gap from the dock's top as from the edges.
        bottom = baseBottom + PREVIEW_CARD_MARGIN,
    ),
    importPreview = importPreview,
    currentPage = currentPage,
    isAddingToReview = isAddingToReview,
    onAction = importController::importFromPreview,
    onChosenChange = { key, isChosen -> importReportState.setImportEntriesChosen(listOf(key), isChosen) },
    onResolutionSelected = importReportState::setImportConflictResolution,
)

/** Between a preview's card and the edges of the dock's surface it stands on. */
private val PREVIEW_CARD_MARGIN = 8.dp
