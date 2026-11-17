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
import androidx.compose.runtime.staticCompositionLocalOf

/** `:app:ios`'s Quick Look opener, provided by `CampfireIosApp`, since UIKit's view controllers stay in that module. */
internal val LocalIosFileOpener = staticCompositionLocalOf { FileOpener { false } }

@Composable
internal actual fun rememberFileOpener(): FileOpener = LocalIosFileOpener.current
