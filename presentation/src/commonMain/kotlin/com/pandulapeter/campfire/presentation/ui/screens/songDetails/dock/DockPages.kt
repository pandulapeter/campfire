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

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper

/**
 * What the pages composed hand the dock: each page's [contents] and its stepper, which says where its reader is headed
 * ([steppers]), by its index, and how wide each song's scale chips are in one line ([scalesWidths]), by the song's key
 * ([songKeyOf] a page's, kept current by the screen), so that a setlist whose pages shift never gives one song another's.
 */
internal class DockPages(var songKeyOf: (Int) -> String?) {
    val contents = mutableStateMapOf<Int, DockContent>()
    val steppers = mutableStateMapOf<Int, SongStepper>()
    val scalesWidths = mutableStateMapOf<String, Dp>()

    /** Keeps how wide the scale chips of the song on [page] are, read as they are measured. */
    fun recordScalesWidth(page: Int, width: Dp) {
        val key = songKeyOf(page) ?: return
        if (scalesWidths[key] != width) scalesWidths[key] = width
    }
}
