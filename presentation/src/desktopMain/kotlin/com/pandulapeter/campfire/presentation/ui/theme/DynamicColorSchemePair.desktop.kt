/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.theme

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import com.pandulapeter.campfire.presentation.ui.platform.PlatformImpersonation

/**
 * None on a desktop, which has no wallpaper colors to offer. Drawn as Android, a wallpaper's colors are stood in for by
 * one of the app's own palettes, so that the color options have the entry they have on a phone.
 */
@Composable
internal actual fun dynamicColorSchemePair(): ColorSchemePair? =
    if (PlatformImpersonation.platform == ImpersonatedPlatform.ANDROID) MaterialColorSchemes.Blue else null
