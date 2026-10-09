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

/** What the platform says about the app's use of the microphone, read where the tuner is shown. */
internal enum class MicrophoneStatus {
    GRANTED,

    /** Never asked: the request will show the system's question. */
    NOT_ASKED,

    /** Refused, and on Android refused for good where [MicrophonePermission.canAskAgain] is false. */
    DENIED,

    /** The platform cannot say (the desktop, a browser without the Permissions API): only opening the input tells. */
    UNKNOWN,
}

/**
 * The microphone permission as the tuner's page asks for it: never at launch and never by opening the tab, only by the
 * button on the page.
 *
 * @param status Read again whenever the app comes back to the front, so that a grant made in the system's settings is
 *   seen without a tap.
 * @param canAskAgain Whether [request] would still show the system's question; Android stops showing it after the
 *   second refusal, and [openSettings] is then the way.
 * @param request Asks for it, null where the platform's only way of asking is opening the input (the desktop, the web),
 *   which is then the caller's to do.
 * @param openSettings Opens the system's page where it is allowed, null where the platform has none to open.
 * @param isAllowedInBrowser Whether a refusal is undone in the browser's site settings, which no page can open.
 */
internal class MicrophonePermission(
    val status: MicrophoneStatus,
    val canAskAgain: Boolean,
    val request: (() -> Unit)?,
    val openSettings: (() -> Unit)?,
    val isAllowedInBrowser: Boolean = false,
)

@Composable
internal expect fun rememberMicrophonePermission(): MicrophonePermission
