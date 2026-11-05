/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.runtime.Composable
import kotlin.js.ExperimentalWasmJsInterop

internal actual val isAppleKeyboard = isApplePlatform()

// A browser cannot say whether a keyboard is attached, so the page goes by its input as the rest of it does.
@Composable
internal actual fun rememberHasHardwareKeyboard() = isDesktopPlatform

private fun isApplePlatform(): Boolean = js(
    """/mac|iphone|ipad|ipod/i.test((navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform || '')"""
)
