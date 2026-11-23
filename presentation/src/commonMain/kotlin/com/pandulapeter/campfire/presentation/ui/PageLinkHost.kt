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
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.pageLink.PageLinkSheet
import com.pandulapeter.campfire.presentation.ui.platform.AppBuild
import com.pandulapeter.campfire.presentation.ui.platform.WEBSITE_URL
import com.pandulapeter.campfire.presentation.ui.platform.appBuild
import com.pandulapeter.campfire.presentation.ui.platform.rememberClipboardTextReader
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportReport

/**
 * [PageLinkSheet] with what its buttons do. Only the web is offered the app, which reads the song from the link alone:
 * a desktop without a web view is the app already. A paste over the failure of the page's own import closes that import
 * screen first, since a pasted text waits in the queue behind a failure on screen, and the editor is what replaces it.
 */
@Composable
internal fun PageLinkHost(
    viewModel: CampfireViewModel,
    dialog: DialogType.PageLink,
    urlOpener: (String) -> Unit,
) {
    val clipboard = rememberClipboardTextReader()
    PageLinkSheet(
        address = dialog.address,
        isAppOffered = appBuild == AppBuild.WEB,
        onOpenPage = { urlOpener(dialog.address) },
        onPaste = { close ->
            viewModel.pastedSongController.newSongFromCopiedPage(clipboard) {
                close()
                if (viewModel.importReportState.report is ImportReport.Failed) viewModel.importReportState.close()
            }
        },
        onGetApp = { urlOpener("$WEBSITE_URL#download") },
        onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
    )
}
