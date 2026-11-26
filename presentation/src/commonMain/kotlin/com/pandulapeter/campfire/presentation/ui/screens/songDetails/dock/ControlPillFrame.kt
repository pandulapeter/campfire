/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

/** How a [DockControlPill] is drawn around its content. */
internal enum class ControlPillFrame {

    /**
     * Filled and outlined: the dock's controls (the tempo chip, the instrument pill), set apart from the chord and scale
     * chips, which are the song's, by their fill, the outline standing them out from the dock's own surface.
     */
    TONAL,

    /**
     * Outlined with no fill: the app bar's key chip, which a fill made read as one of the song's section headers, those
     * being filled pills as well.
     */
    OUTLINED,

    /** Neither: a chip that opens nothing (the key chip in read only mode), which then reads as text rather than inviting a press. */
    NONE,
}
