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

/**
 * The palette the operating system derived from the user's wallpaper, or null where there is none to be had - which
 * is every platform but Android, and Android below 12, where the system has no such colors to hand out.
 *
 * It is read rather than stored: the wallpaper can change while the app is installed, so the preference only says
 * that the system decides, never what it decided. Null is also what the settings screen asks about, so that the
 * choice is offered exactly where it can be honored.
 */
@Composable
internal expect fun dynamicColorSchemePair(): ColorSchemePair?
