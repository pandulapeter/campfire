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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_log_email
import com.pandulapeter.campfire.presentation.resources.settings_log_save
import com.pandulapeter.campfire.presentation.resources.settings_log_share
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker

/**
 * What sending the log is called here, which is what it does here: the Settings row and the dialog it opens, see
 * `ExportController.sendProcessLog`.
 */
@Composable
internal fun processLogActionTitle(filePicker: FilePicker) = stringResource(
    when {
        filePicker.canEmailFiles -> Res.string.settings_log_email
        filePicker.canShare -> Res.string.settings_log_share
        else -> Res.string.settings_log_save
    },
)
