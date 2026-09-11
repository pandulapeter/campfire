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

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun dynamicColorSchemePair(): ColorSchemePair? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val context = LocalContext.current
    remember(context) {
        ColorSchemePair(
            light = dynamicLightColorScheme(context),
            dark = dynamicDarkColorScheme(context),
        )
    }
} else {
    null
}
