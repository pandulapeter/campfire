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
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

internal actual val areBeatHapticsFeltInBackground = false

/** UIKit's feedback generators may only be used on the main thread. */
internal actual val beatHapticsDispatcher: CoroutineDispatcher = Dispatchers.Main

/** Prepared ahead, so that the first beat is not late by the time the engine takes to wake up. */
@Composable
internal actual fun rememberBeatHaptics(): BeatHaptics? = remember {
    val heavy = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).apply { prepare() }
    val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).apply { prepare() }
    BeatHaptics { isAccent ->
        val generator = if (isAccent) heavy else light
        generator.impactOccurred()
        generator.prepare()
    }
}
