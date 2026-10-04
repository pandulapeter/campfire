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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions
import platform.UIKit.UIKeyboardTypeNumbersAndPunctuation

/** The numbers page of the full keyboard, which has the return key the number pad lacks; the field drops anything but digits. */
@OptIn(ExperimentalComposeUiApi::class)
internal actual val numericPlatformImeOptions: PlatformImeOptions? = PlatformImeOptions { keyboardType(UIKeyboardTypeNumbersAndPunctuation) }
