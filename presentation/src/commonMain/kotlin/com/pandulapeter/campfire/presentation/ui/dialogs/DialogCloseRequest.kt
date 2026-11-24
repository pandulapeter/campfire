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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the dialog drawn here has been asked to close by the stack of open dialogs it is in ([DialogHost.closeRequest]),
 * which it does as one of its own ways out would: a sheet slides away, a question goes unanswered.
 */
internal val LocalIsDialogCloseRequested = staticCompositionLocalOf { false }

/** Calls [close] once the dialog drawn here is asked to close, see [LocalIsDialogCloseRequested]. */
@Composable
internal fun CloseWhenRequested(close: () -> Unit) {
    val isRequested = LocalIsDialogCloseRequested.current
    val latestClose by rememberUpdatedState(close)
    LaunchedEffect(isRequested) { if (isRequested) latestClose() }
}
