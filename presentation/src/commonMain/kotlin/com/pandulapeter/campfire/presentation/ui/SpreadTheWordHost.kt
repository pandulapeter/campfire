/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.spread_the_word_share_text
import com.pandulapeter.campfire.presentation.ui.dialogs.spreadTheWord.SpreadTheWordDialog
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.WEBSITE_URL
import com.pandulapeter.campfire.presentation.ui.platform.isCopyConfirmedBySystem
import com.pandulapeter.campfire.presentation.ui.platform.plainTextClipEntry
import com.pandulapeter.campfire.presentation.ui.platform.platformStore
import com.pandulapeter.campfire.presentation.ui.platform.storeName
import kotlinx.coroutines.launch

/**
 * [SpreadTheWordDialog] with what its buttons do. Share sends the website's address, never a store listing, since the
 * friend may be on another platform and the website offers every one: through the share sheet where there is one, and
 * elsewhere copied, with a snackbar saying so. Rate opens the store's own review page rather than an in-app review
 * API, which is throttled, may show nothing, and is not to be called from a button.
 */
@Composable
internal fun SpreadTheWordHost(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
) {
    val filePicker = LocalFilePicker.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shareText = stringResource(Res.string.spread_the_word_share_text, WEBSITE_URL)
    val store = platformStore?.takeIf { it.reviewUrl != null }
    SpreadTheWordDialog(
        rateStoreName = store?.storeName,
        onShare = {
            if (filePicker.canShare) {
                viewModel.exportController.shareLink(filePicker = filePicker, link = shareText, subject = "Campfire")
                viewModel.dialogHost.dismissDialog()
            } else {
                // Closed only once copied, since the copy runs in the dialog's own scope.
                scope.launch {
                    clipboard.setClipEntry(plainTextClipEntry(shareText))
                    if (!isCopyConfirmedBySystem) viewModel.messageSink.sendMessage(Message.LinkCopied)
                    viewModel.dialogHost.dismissDialog()
                }
            }
        },
        onRate = {
            viewModel.dialogHost.dismissDialog()
            store?.reviewUrl?.let(urlOpener)
        },
        onDismiss = viewModel.dialogHost::dismissDialog,
    )
}
