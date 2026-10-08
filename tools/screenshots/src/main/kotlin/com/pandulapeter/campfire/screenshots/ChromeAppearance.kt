/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily

/**
 * How the chrome is drawn for one shot: in the light or the dark appearance of the app's theme, in the platform's
 * own font, and with the app's icon where the system shows it (a shelf, a taskbar).
 */
internal data class ChromeAppearance(
    val isDark: Boolean,
    val fontFamily: FontFamily,
    val appIcon: ImageBitmap?,
) {
    val content: Color get() = if (isDark) Color.White else Color(0xFF1B1B1F)
}
