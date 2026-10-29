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

import androidx.compose.animation.Crossfade
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.crash_report_not_now
import com.pandulapeter.campfire.presentation.resources.crash_report_text
import com.pandulapeter.campfire.presentation.resources.crash_report_title
import com.pandulapeter.campfire.presentation.resources.log_dialog_address_copied
import com.pandulapeter.campfire.presentation.resources.log_dialog_copy_address
import com.pandulapeter.campfire.presentation.resources.log_dialog_email
import com.pandulapeter.campfire.presentation.resources.log_dialog_send_to
import com.pandulapeter.campfire.presentation.resources.log_dialog_share
import com.pandulapeter.campfire.presentation.resources.log_dialog_text
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.ui.platform.DEVELOPER_EMAIL
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.forgetPendingCrashReport
import com.pandulapeter.campfire.presentation.ui.platform.isCopyConfirmedBySystem
import com.pandulapeter.campfire.presentation.ui.platform.plainTextClipEntry
import com.pandulapeter.campfire.presentation.ui.platform.canShareProcessLog
import kotlinx.coroutines.launch

/**
 * Says what the log holds before it goes anywhere, which is the privacy warning: the screens opened and the songs' names
 * are in it. Where the log can only be shared or saved, it also says where to send it, with a button that copies the
 * address, which a mail app opened by hand needs typed otherwise, and says so on itself rather than in a snackbar, which
 * the dialog's scrim would cover (Android 13 and later say so themselves). After a crash it first says what
 * happened, and either answer forgets the crash, so that it is asked about once.
 */
@Composable
internal fun SendProcessLogDialog(
    isAfterCrash: Boolean,
    onSend: (FilePicker) -> Unit,
    onDismiss: () -> Unit,
) {
    val filePicker = LocalFilePicker.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var isAddressCopied by remember { mutableStateOf(false) }
    val canEmail = filePicker.canEmailFiles
    val text = listOfNotNull(
        stringResource(Res.string.crash_report_text).takeIf { isAfterCrash },
        stringResource(Res.string.log_dialog_text),
        stringResource(Res.string.log_dialog_send_to, DEVELOPER_EMAIL).takeIf { !canEmail },
    ).joinToString("\n\n")
    ConfirmationDialog(
        title = if (isAfterCrash) stringResource(Res.string.crash_report_title) else processLogActionTitle(filePicker),
        text = text,
        confirmLabel = stringResource(
            when {
                canEmail -> Res.string.log_dialog_email
                filePicker.canShareProcessLog -> Res.string.log_dialog_share
                else -> Res.string.save
            },
        ),
        isDestructive = false,
        extraButton = if (canEmail) {
            null
        } else {
            {
                TextButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(plainTextClipEntry(DEVELOPER_EMAIL))
                            if (!isCopyConfirmedBySystem) isAddressCopied = true
                        }
                    },
                ) {
                    Crossfade(isAddressCopied) { isCopied ->
                        Text(stringResource(if (isCopied) Res.string.log_dialog_address_copied else Res.string.log_dialog_copy_address))
                    }
                }
            }
        },
        dismissLabel = stringResource(if (isAfterCrash) Res.string.crash_report_not_now else Res.string.cancel),
        onDismiss = {
            if (isAfterCrash) forgetPendingCrashReport()
            onDismiss()
        },
        onConfirm = {
            if (isAfterCrash) forgetPendingCrashReport()
            onSend(filePicker)
        },
    )
}
