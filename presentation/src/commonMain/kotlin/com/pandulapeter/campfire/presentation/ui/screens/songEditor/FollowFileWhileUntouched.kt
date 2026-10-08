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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.pandulapeter.campfire.chordpro.ChordProSummaryCache
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Keeps an editor that holds nothing of its own in step with its file. Once the file changes underneath it - a sync
 * run, a rescan, another program - a field still holding the text the file had would otherwise count as unsaved, and
 * saving it would write the old version back over the new one, which the next sync run then carries to every device.
 * So the field follows the file for as long as it holds exactly what the file held before the change: anything typed,
 * or a draft reopened on launch, never equals that and is left alone, and a file that has gone leaves the field as it
 * is, since then the field is the only copy. The update is one step of the undo history, like a revert.
 */
@Composable
internal fun FollowFileWhileUntouched(
    viewModel: CampfireViewModel,
    fileName: String,
    textFieldState: TextFieldState,
    summaryCache: ChordProSummaryCache,
) {
    val notation = viewModel.editorNotation
    LaunchedEffect(textFieldState, fileName) {
        val editorTextOf = { text: String -> viewModel.songRenderer.editorTextOf(text, notation) }
        var previous = viewModel.songTexts.value[fileName]?.let(editorTextOf)
        viewModel.songTexts.map { it[fileName]?.let(editorTextOf) }.distinctUntilChanged().collect { current ->
            val base = previous
            previous = current
            if (current != null && base != null && current != base && textFieldState.text.contentEquals(base)) {
                summaryCache.clear()
                textFieldState.replaceAll(current, isSelectionMapped = true)
            }
        }
    }
}
