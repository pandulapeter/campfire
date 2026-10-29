/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition

/**
 * Where the settings screen is scrolled to: each tab's page on its own, and the row of tabs where they are wider than the
 * window. The row is kept because it slides to the open tab on its first layout unless it is already there, so one that
 * starts at General's end every time would slide across whenever the screen reopens on a tab past the window's edge.
 */
internal class SettingsScrollPositions {

    val pages = SettingsTab.entries.associateWith { ScrollPosition() }

    val tabRow = ScrollPosition()
}
