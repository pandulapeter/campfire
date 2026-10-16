/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.about

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.add_songs_hint_title
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.AddSongsTips
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.rememberClipboardTextReader

/**
 * The songs screen's [AddSongsTips] card for any library, opened from Settings → Library. Both buttons close the dialog
 * before they start: a pasted song's editor waits for every open dialog to leave, and the file picker should not come
 * back to a hint that has done its job.
 */
@Composable
internal fun AddSongsHintDialog(
    viewModel: CampfireViewModel,
) {
    val isImporting by viewModel.importController.isImporting.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.preferencesController.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val filePicker = LocalFilePicker.current
    val clipboardReader = rememberClipboardTextReader()
    AlertDialog(
        modifier = Modifier.widthIn(max = 480.dp),
        onDismissRequest = viewModel.dialogHost::dismissDialog,
        title = { Text(stringResource(Res.string.add_songs_hint_title)) },
        text = {
            val scrollState = rememberScrollState()
            AddSongsTips(
                modifier = Modifier.fillMaxWidth().fadingVerticalEdges(scrollState).bounceVerticalScroll(scrollState),
                isEnabled = !isImporting && !isPerformanceModeEnabled,
                onPaste = {
                    viewModel.dialogHost.dismissDialog()
                    viewModel.pastedSongController.newSongFromClipboard(clipboardReader)
                },
                onImport = {
                    viewModel.dialogHost.dismissDialog()
                    viewModel.importController.importFiles(filePicker)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel.dialogHost::dismissDialog) { Text(stringResource(Res.string.close)) }
        },
    )
}
