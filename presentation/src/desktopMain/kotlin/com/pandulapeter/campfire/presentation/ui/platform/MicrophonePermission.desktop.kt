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

/**
 * The JVM cannot ask macOS or Windows whether it may record, so the status is always unknown and opening the input is
 * the question (macOS shows its prompt then; Windows answers a refusal with silence). The settings are the system's
 * microphone privacy page, which Linux has none of.
 */
@Composable
internal actual fun rememberMicrophonePermission(): MicrophonePermission = remember {
    val operatingSystem = System.getProperty("os.name").orEmpty().lowercase()
    val settingsUrl = when {
        "mac" in operatingSystem || "darwin" in operatingSystem -> "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone"
        "win" in operatingSystem -> "ms-settings:privacy-microphone"
        else -> null
    }
    MicrophonePermission(
        status = MicrophoneStatus.UNKNOWN,
        canAskAgain = true,
        request = null,
        openSettings = settingsUrl?.let { url -> { openUrl(url) } },
    )
}
