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

/**
 * Which of the app's icons follows the theme color on the platform the app runs on, which is what the settings screen
 * names the switch for it after and describes it with: every platform lets the app change a different one, and each
 * has its own catch.
 */
internal enum class AppIconSurface {

    /** Android's launcher entry, which changes when the user leaves the app the first time, and may leave the home screen. */
    LAUNCHER,

    /** iOS's home screen icon, which the system confirms every change of with an alert. */
    HOME_SCREEN,

    /** The macOS Dock icon, for as long as the app runs. */
    DOCK,

    /** The Windows taskbar button and title bar, for as long as the app runs; the Start menu keeps the packaged one. */
    TASKBAR,

    /** The window icon a Linux window manager draws, for as long as the app runs. */
    WINDOW,

    /** The browser tab's icon. */
    BROWSER_TAB,
}
