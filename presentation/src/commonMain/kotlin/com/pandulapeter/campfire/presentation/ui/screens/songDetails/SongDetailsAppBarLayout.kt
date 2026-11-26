/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.menu.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.coverArt.APP_BAR_COVER_GAP
import com.pandulapeter.campfire.presentation.ui.components.coverArt.APP_BAR_COVER_SIZE

/** What the song details app bar has the room for, see [appBarButtons]. */
internal data class AppBarButtons(
    val isCoverShown: Boolean,
    val isSetlistAssignmentsShown: Boolean,
)

/**
 * What the app bar of a screen [appBarWidth] wide has the room for besides what it always holds: the back button and the
 * overflow button ([APP_BAR_FIXED_CONTENT_WIDTH]), which holds the way into the editor, the tuner, and the text size
 * whatever the width, and in read only mode ([isReadOnly]) the read only mark too ([PERFORMANCE_FIXED_CONTENT_WIDTH]).
 * The key chip ([hasKeyChip], the chords on) is always in the bar, so its room ([KEY_CHIP_ROOM]) is taken first. The
 * rest comes out of that menu one at a time as the bar widens, each needing the title left [MIN_TITLE_WIDTH] beside it
 * and everything before it: the cover in front of the title, which only decorates it - where [hasCover] says no song of
 * the pager has one, it takes no room - then the setlist assignments (never in read only mode).
 */
internal fun appBarButtons(appBarWidth: Dp, hasCover: Boolean, isReadOnly: Boolean, hasKeyChip: Boolean): AppBarButtons {
    val fixedContentWidth = (if (isReadOnly) PERFORMANCE_FIXED_CONTENT_WIDTH else APP_BAR_FIXED_CONTENT_WIDTH) + if (hasKeyChip) KEY_CHIP_ROOM else 0.dp
    val room = appBarWidth - fixedContentWidth - if (hasCover) APP_BAR_COVER_SIZE + APP_BAR_COVER_GAP else 0.dp
    val setlistAssignmentsWidth = if (isReadOnly) 0.dp else APP_BAR_OVERLAPPING_ACTION_WIDTH
    return AppBarButtons(
        isCoverShown = room >= MIN_TITLE_WIDTH,
        isSetlistAssignmentsShown = !isReadOnly && room - setlistAssignmentsWidth >= MIN_TITLE_WIDTH,
    )
}

/**
 * The room the bar keeps for its key chip (`AppBarKeyChip`): its label and a key ("Key of Am", "Em shapes"), or
 * with a capo under it, at the interface's text size (`KeyChip`), and the gap that parts it from the title. Reserved by
 * a constant rather than measured: what the chip says differs from page to page, and the bar decides for every page at
 * once, so a width that followed the page would send the cover and the star into the menu and back as a setlist's pages
 * turn. It is as much as leaves the smallest phone's bar (360 dp) the title's [MIN_TITLE_WIDTH] beside the star; a
 * wider chip ("F#m (+11)", a long word for the capo) takes the rest from the title, which is cut short.
 */
internal val KEY_CHIP_ROOM = 80.dp + APP_BAR_KEY_CHIP_GAP

private val APP_BAR_NAVIGATION_WIDTH = 52.dp // The 48dp button and the 4dp the bar pads its start by.
private val APP_BAR_END_PADDING = 4.dp
private val APP_BAR_ACTION_WIDTH = 48.dp

/** What one more button adds to a row of them, which reach into each other's touch targets (see [ACTION_BUTTON_OVERLAP]). */
private val APP_BAR_OVERLAPPING_ACTION_WIDTH = APP_BAR_ACTION_WIDTH - ACTION_BUTTON_OVERLAP

/** The back button, the bar's paddings and the overflow button. */
private val APP_BAR_FIXED_CONTENT_WIDTH = APP_BAR_NAVIGATION_WIDTH + APP_BAR_END_PADDING + APP_BAR_ACTION_WIDTH

/** What performance mode's bar always holds: the same, and the read only mark reaching into the overflow button. */
private val PERFORMANCE_FIXED_CONTENT_WIDTH = APP_BAR_FIXED_CONTENT_WIDTH + APP_BAR_OVERLAPPING_ACTION_WIDTH

/** Enough of a title to tell which song is up, with its artist under it: what the 360 dp bar leaves beside the key chip and the star. */
private val MIN_TITLE_WIDTH = 132.dp
