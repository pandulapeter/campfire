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
 * None on a desktop, which has nothing to tap the hand with. Drawn as a phone, a beat goes nowhere, but the Metronome
 * tab offers the switch it offers there.
 */
@Composable
internal actual fun rememberBeatHaptics(): BeatHaptics? = when (PlatformImpersonation.platform) {
    ImpersonatedPlatform.ANDROID, ImpersonatedPlatform.IOS -> BeatHaptics {}
    ImpersonatedPlatform.MACOS, ImpersonatedPlatform.WINDOWS, null -> null
}
