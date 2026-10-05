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
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

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
