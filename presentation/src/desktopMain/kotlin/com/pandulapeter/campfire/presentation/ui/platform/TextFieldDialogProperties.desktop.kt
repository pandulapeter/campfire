/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/** Keeps the typed dialog's window sizing consistent with the form its content draws. */
internal actual fun textFieldDialogProperties(isFullScreen: Boolean): DialogProperties = DialogProperties(
    usePlatformDefaultWidth = !isFullScreen,
)

internal actual val isTextFieldDialogWindowFullSize: Boolean = false

@Composable
internal actual fun Modifier.textFieldDialogInsetsPadding(isFullScreen: Boolean): Modifier = this
