/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.share.ShareTarget
import com.pandulapeter.campfire.presentation.ui.share.shareTargetOf

/**
 * However the export screen goes - closed, Escape, the web's Back, another dialog put over it - it stays drawn while it
 * slides away, so its own disposal would be too late: its options are saved and its drawing cancelled here, a screen
 * opened again in the next moment finds the options it left, and its Save, Share and options do nothing once it is no
 * longer the dialog on screen. Registered by the view model with the dialog host.
 */
internal fun ExportController.onBeforeDialogChange(
    previousDialog: DialogType?,
    dialogType: DialogType?,
    forgetShareCreation: (ShareTarget) -> Unit,
) {
    if (previousDialog is DialogType.Export && dialogType != previousDialog) {
        flushPrintSettings()
        // How sharing it last went is for this screen only: opened again, it offers to share afresh.
        shareTargetOf(previousDialog)?.let(forgetShareCreation)
        // An export nobody is looking at any more would put its picker up over whatever is on screen by then.
        cancelPdfExport()
    }
}
