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

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/**
 * The typography the interface is set in, or null for as long as the font it needs is still on its way.
 *
 * Material's own everywhere but the web, which is answered at once: its generic sans serif family resolves to the
 * system's font there (SF Pro, Segoe UI, Roboto). The web build draws with its own copy of Skia, which cannot reach
 * the browser's fonts and carries a single weight of Roboto instead - so every style Material sets in Medium, the
 * titles, labels, tabs and buttons, came out in Regular, and the whole interface read as thin. That build bundles a
 * font with the weights Material asks for.
 *
 * [CampfireTheme] does not report itself settled until this has answered, which holds the launch screen over the app
 * rather than letting every line of text in it be laid out again once the font lands.
 */
@Composable
internal expect fun interfaceTypography(): Typography?
