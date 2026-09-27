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
 * Whether the operating system is set to its dark appearance, followed while it changes as long as it is read: the
 * "System" theme is expected to switch together with the rest of the desktop, not on the next start.
 */
@Composable
internal expect fun isSystemInDarkThemeLive(): Boolean
