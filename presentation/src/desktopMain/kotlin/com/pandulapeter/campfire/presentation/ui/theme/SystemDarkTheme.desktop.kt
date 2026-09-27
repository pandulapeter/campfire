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
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

/**
 * Polls [currentSystemTheme], since Compose on the JVM reads the system's appearance once, when the window is created,
 * and hears nothing of a change after that - and neither AWT nor Skiko has a notification to wait for instead. The
 * question is one native call, so asking it a few times a second costs nothing noticeable, and it is only asked while
 * the "System" theme is selected, which is the only composition that reads it. Linux answers
 * [SystemTheme.UNKNOWN], which is taken as light, the way Compose's own [androidx.compose.foundation.isSystemInDarkTheme]
 * takes it.
 */
@Composable
internal actual fun isSystemInDarkThemeLive() = produceState(currentSystemTheme == SystemTheme.DARK) {
    while (true) {
        delay(250)
        value = currentSystemTheme == SystemTheme.DARK
    }
}.value
