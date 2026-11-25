/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/** What is drawn over a song scrolling by itself, which decides whether it stops, pauses, or plays. */
internal enum class AutoScrollCover {

    /** Nothing: the song is read, and plays on where it was paused under a [DOCK_SHEET]. */
    NONE,

    /** A chord's or a scale's sheet, glanced at to play on: playing pauses, and goes on once it is put away. */
    DOCK_SHEET,

    /** Anything else (another sheet or dialog, another screen), which stops playing for good. */
    OTHER,
}

/**
 * What covers the song details screen, with [visibleDialog] drawn over it and [top] on top of the back stack. A chord's or
 * a scale's sheet is a [AutoScrollCover.DOCK_SHEET]: over song details they open only from the dock (the song's chord
 * names have no gesture), and one opened from another (a scale sheet's chord) is still one.
 */
internal fun autoScrollCoverOf(visibleDialog: DialogType?, top: CampfireDestination?) = when {
    top !is CampfireDestination.SongDetails -> AutoScrollCover.OTHER
    visibleDialog == null -> AutoScrollCover.NONE
    visibleDialog is DialogType.ChordShape || visibleDialog is DialogType.ChordReference || visibleDialog is DialogType.Scale -> AutoScrollCover.DOCK_SHEET
    else -> AutoScrollCover.OTHER
}
