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
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.confirm_exit_message
import com.pandulapeter.campfire.presentation.resources.confirm_exit_title
import com.pandulapeter.campfire.presentation.resources.import_exit_confirm
import com.pandulapeter.campfire.presentation.resources.import_exit_keep
import com.pandulapeter.campfire.presentation.resources.import_exit_message
import com.pandulapeter.campfire.presentation.resources.import_exit_title
import com.pandulapeter.campfire.presentation.ui.dialogs.ConfirmationDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.state.AppExitController

/**
 * The questions closing the application may ask (`AppExitController`): whether to close it at all after Escape, and
 * whether to stop an import still being read. Any other [dialog] shows nothing.
 */
@Composable
internal fun ExitQuestionDialog(
    dialog: DialogType,
    appExitController: AppExitController,
    dialogHost: DialogHost,
) {
    when (dialog) {
        DialogType.ConfirmExit -> ConfirmationDialog(
            title = stringResource(Res.string.confirm_exit_title),
            text = stringResource(Res.string.confirm_exit_message),
            confirmLabel = stringResource(Res.string.close),
            isDestructive = false,
            onDismiss = dialogHost::dismissDialog,
            onConfirm = appExitController::exitConfirmed,
        )

        DialogType.StopImportBeforeExit -> ConfirmationDialog(
            title = stringResource(Res.string.import_exit_title),
            text = stringResource(Res.string.import_exit_message),
            confirmLabel = stringResource(Res.string.import_exit_confirm),
            dismissLabel = stringResource(Res.string.import_exit_keep),
            onDismiss = dialogHost::dismissDialog,
            onConfirm = appExitController::stopImportAndExit,
        )

        else -> Unit
    }
}
