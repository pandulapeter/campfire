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

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Puts a home screen widget showing one setlist on the home screen, where the platform lets an app ask: Android, whose
 * launcher asks the user in a popup of its own. iOS has no such request (a widget is only ever added by the user), so
 * there, and wherever the launcher cannot, there is none, and the setlists' Add to home screen is not offered.
 */
fun interface SetlistWidgetPinner {
    fun pin(setlistFileName: String)
}

/** The shell's [SetlistWidgetPinner], or null where there is none. */
val LocalSetlistWidgetPinner = staticCompositionLocalOf<SetlistWidgetPinner?> { null }
