/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.shortcuts

/**
 * Where a [KeyShortcut] is heard, which is the group the shortcuts sheet and the system's own lists (Android's Meta + /,
 * iOS's Command-hold overlay) show it under, in this order.
 */
internal enum class ShortcutScope {
    /** Wherever nothing is drawn over the screen, a sheet's Save and Escape over anything. */
    APP,

    /** The songs and the setlists screens. */
    LISTS,

    /** A song being read. */
    SONG,

    /** The click, on a song and on the Metronome tab. */
    METRONOME,

    /** The song editor and its search. */
    EDITOR,

    /** The export screen's print preview. */
    PRINT_PREVIEW,
}
