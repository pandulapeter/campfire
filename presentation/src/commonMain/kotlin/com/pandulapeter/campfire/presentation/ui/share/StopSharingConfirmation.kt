/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.share_stop
import com.pandulapeter.campfire.presentation.resources.share_stop_confirmation
import com.pandulapeter.campfire.presentation.ui.dialogs.ConfirmationDialog

/**
 * Asks before the share of the item titled [title] is stopped, since its link then stops working for everyone, for good.
 * Composed by the screen it is asked on rather than put on the dialog stack: the export screen is a dialog itself, and
 * would slide away under it.
 */
@Composable
internal fun StopSharingConfirmation(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) = ConfirmationDialog(
    title = stringResource(Res.string.share_stop),
    text = textResource(Res.string.share_stop_confirmation, title),
    confirmLabel = stringResource(Res.string.share_stop),
    onDismiss = onDismiss,
    onConfirm = onConfirm,
)
