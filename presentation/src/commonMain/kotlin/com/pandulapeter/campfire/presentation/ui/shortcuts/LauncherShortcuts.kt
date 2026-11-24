/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.shortcuts

/**
 * The launcher shortcuts as the shells hand them to the system, in the app's language.
 *
 * @property offered Every shortcut offered ([LauncherShortcut.offered]), in its order, with its label.
 * @property withdrawnMessage Why a shortcut is no longer offered, which Android shows on one pinned to the home screen
 *   that it cannot remove.
 */
data class LauncherShortcuts(
    val offered: List<Labelled>,
    val withdrawnMessage: String,
) {

    /** [shortcut] named [label]. */
    data class Labelled(
        val shortcut: LauncherShortcut,
        val label: String,
    )
}
