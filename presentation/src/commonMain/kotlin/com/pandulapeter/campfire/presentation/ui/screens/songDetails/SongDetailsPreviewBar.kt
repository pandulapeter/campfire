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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportController
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportReportState

/** [ImportPreviewBar] where the song details screen puts it: a card over the bottom of the pages, kept off the edges. */
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
    modifier = Modifier
        .padding(
            start = contentPadding.calculateStartPadding(layoutDirection) + PREVIEW_BAR_MARGIN,
            end = contentPadding.calculateEndPadding(layoutDirection) + PREVIEW_BAR_MARGIN,
            bottom = baseBottom + PREVIEW_BAR_MARGIN,
        )
        .widthIn(max = PREVIEW_BAR_MAX_WIDTH),
    importPreview = importPreview,
    currentPage = currentPage,
    isAddingToReview = isAddingToReview,
    onAction = importController::importFromPreview,
    onChosenChange = { key, isChosen -> importReportState.setImportEntriesChosen(listOf(key), isChosen) },
    onResolutionSelected = importReportState::setImportConflictResolution,
)

/** Between a preview's card and the edges around it, and between it and the last line of the song above it. */
private val PREVIEW_BAR_MARGIN = 16.dp

/** A preview's card stays a card, centred, rather than a strip across a wide window, whose message would sit far from its buttons. */
private val PREVIEW_BAR_MAX_WIDTH = 720.dp
