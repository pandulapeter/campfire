/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_editor_edit
import com.pandulapeter.campfire.presentation.resources.song_editor_preview
import com.pandulapeter.campfire.presentation.resources.song_editor_split
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice

/**
 * The editor's control row: transposing, switching pane and folding the insertions away, the things here that do not
 * write at the caret, and so the ones that stay when the insertions leave.
 */
@Composable
internal fun EditorControlRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    key: String?,
    isTranspositionEnabled: Boolean,
    onTransposed: (Int) -> Unit,
    panes: EditorPanes,
    hasRoomForSplitPanes: Boolean,
    onPanesSelected: (EditorPanes) -> Unit,
    isToolbarExpanded: Boolean,
    isToolbarToggleLabeled: Boolean,
    onToolbarToggled: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    Row(
        modifier = modifier.padding(
            start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = contentPadding.calculateEndPadding(layoutDirection) + 4.dp,
            bottom = 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextTranspositionControls(
            key = key,
            isEnabled = isTranspositionEnabled,
            onTransposed = onTransposed,
        )
        SegmentedChoice(
            // Not the whole of a desktop window's width: the row is a pair of controls rather than a
            // tab bar, and the stepper next to it would be lost at the end of a metre of segments.
            modifier = Modifier.weight(1f, fill = false).widthIn(max = PANE_CHOICE_MAX_WIDTH),
            options = listOfNotNull(
                EditorPanes.EDIT to stringResource(Res.string.song_editor_edit),
                EditorPanes.PREVIEW to stringResource(Res.string.song_editor_preview),
                if (hasRoomForSplitPanes) EditorPanes.SPLIT to stringResource(Res.string.song_editor_split) else null,
            ),
            selected = panes,
            isInline = true,
            shouldApplyPadding = false,
            onSelected = onPanesSelected,
        )
        // Disabled rather than hidden over a preview, so that the segments next to it do not change
        // width under the finger that has just picked one of them.
        EditorToolbarToggle(
            isExpanded = isToolbarExpanded,
            isLabeled = isToolbarToggleLabeled,
            isEnabled = panes != EditorPanes.PREVIEW,
            onToggled = onToolbarToggled,
        )
    }
}

private val PANE_CHOICE_MAX_WIDTH = 400.dp
