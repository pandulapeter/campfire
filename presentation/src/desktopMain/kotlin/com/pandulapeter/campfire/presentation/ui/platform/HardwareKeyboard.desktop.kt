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

internal actual val isAppleKeyboard
    get() = when (PlatformImpersonation.platform) {
        ImpersonatedPlatform.MACOS, ImpersonatedPlatform.IOS -> true
        ImpersonatedPlatform.WINDOWS, ImpersonatedPlatform.ANDROID -> false
        null -> System.getProperty("os.name").orEmpty().lowercase().let { it.contains("mac") || it.contains("darwin") }
    }

@Composable
internal actual fun rememberHasHardwareKeyboard() = when (PlatformImpersonation.platform) {
    ImpersonatedPlatform.ANDROID, ImpersonatedPlatform.IOS -> false
    ImpersonatedPlatform.MACOS, ImpersonatedPlatform.WINDOWS, null -> true
}
