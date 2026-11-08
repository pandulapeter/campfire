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
 * What of the settings screen lasts the session besides its tab: where each tab's page is scrolled to, and the row of
 * tabs where they are wider than the window. The row is kept because it slides to
 * the open tab on its first layout unless it is already there, so one that starts at the first tab every time would
 * slide across whenever the screen reopens on a tab past the window's edge.
 */
internal class SettingsSession {

    val pages = SettingsTab.entries.associateWith { ScrollPosition() }

    val tabRow = ScrollPosition()
}
