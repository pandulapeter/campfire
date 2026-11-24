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
import androidx.compose.runtime.LaunchedEffect
import com.pandulapeter.campfire.presentation.ui.platform.rememberClipboardTextReader
import kotlinx.coroutines.flow.Flow

/**
 * Opens the app's own `campfire://` links the system hands the shell ([openAppLink]), a launcher shortcut's among them:
 * never a shared link or the sign-in redirect, which the shells take first. Android and iOS only, which have shortcuts.
 */
@Composable
internal fun AppLinksEffect(viewModel: CampfireViewModel, appLinks: Flow<String>) {
    val clipboard = rememberClipboardTextReader()
    LaunchedEffect(viewModel, appLinks, clipboard) { appLinks.collect { viewModel.openAppLink(link = it, clipboard = clipboard) } }
}
