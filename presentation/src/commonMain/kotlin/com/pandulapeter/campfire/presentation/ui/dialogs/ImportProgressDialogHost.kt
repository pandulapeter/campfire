/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.import_progress_title
import kotlinx.coroutines.delay

/**
 * The progress of an import, shown only once it has taken longer than [PROGRESS_DIALOG_DELAY_MILLIS]: most imports
 * are a file or two and over before a window could be read, and one that flashed up for a frame would only say that
 * something went past. It waits while another dialog is up rather than covering it, since that dialog may hold input.
 *
 * Its Cancel ends the preparation while nothing has been written yet (see [isCancellable]), and fades out once the
 * writing starts. Tapping outside or Back never cancels: the import is not something to lose by a stray gesture.
 * Cancellation is noticed where the preparation yields, between files and songs, so a single huge document being
 * extracted is not interrupted until it is read; on the web, where everything shares one thread, the click itself is
 * only handled once the preparation yields.
 */
@Composable
internal fun ImportProgressDialogHost(
    progress: ImportProgress?,
    canShow: Boolean,
    onCancel: () -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(progress != null, canShow) {
        isVisible = false
        if (progress != null && canShow) {
            delay(PROGRESS_DIALOG_DELAY_MILLIS)
            isVisible = true
        }
    }
    if (isVisible && progress != null && canShow) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(Res.string.import_progress_title)) },
            text = { ImportProgressContent(progress = progress) },
            confirmButton = {},
            dismissButton = {
                AnimatedVisibility(
                    visible = progress.phase.isCancellable,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** The phases that only read and compare: the ones after them write, and an import is never left half written. */
internal val ImportProgress.Phase.isCancellable
    get() = this == ImportProgress.Phase.UNPACKING || this == ImportProgress.Phase.READING || this == ImportProgress.Phase.COMPARING

private const val PROGRESS_DIALOG_DELAY_MILLIS = 400L
