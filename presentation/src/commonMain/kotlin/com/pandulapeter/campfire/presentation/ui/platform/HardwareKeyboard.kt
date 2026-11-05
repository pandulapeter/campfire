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

/**
 * Whether keyboard shortcuts are written with Apple's symbols (`⌘S`) rather than with the modifiers' names
 * (`Ctrl+S`): on macOS and iOS, and in a browser running on either. Cmd and Ctrl both work everywhere either way.
 */
internal expect val isAppleKeyboard: Boolean

/**
 * Whether the device has a physical keyboard to press shortcuts on, which is when their hints are shown: always on the
 * desktop and a desktop browser, on Android and iOS only while one is attached (a tablet's keyboard folded behind it
 * counts as gone), recomposing as it comes and goes.
 */
@Composable
internal expect fun rememberHasHardwareKeyboard(): Boolean
