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

import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingTools
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.menu.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.coverArt.APP_BAR_COVER_GAP
import com.pandulapeter.campfire.presentation.ui.components.coverArt.APP_BAR_COVER_SIZE

/** What the song details app bar has the room for, see [appBarButtons]. */
internal data class AppBarButtons(
    val isCoverShown: Boolean,
    val isSetlistAssignmentsShown: Boolean,
    val isFontScaleShown: Boolean,
)

/**
 * What the app bar of a screen [appBarWidth] wide has the room for besides what it always holds: the back button and the
 * overflow button ([APP_BAR_FIXED_CONTENT_WIDTH]), which holds the way into the editor whatever the width (the metronome
 * and the tuner are the dock's, see `SongPlayingTools`), and in read only mode ([isReadOnly]) the
 * read only mark too ([PERFORMANCE_FIXED_CONTENT_WIDTH]). The rest comes out of that menu one at a time as the bar
 * widens, each needing the title left [MIN_TITLE_WIDTH] beside it and everything before it: the cover in front of the
 * title, which only decorates it - where [hasCover] says no song of the pager has one, it takes no room - then the
 * setlist assignments (never in read only mode), then the text size stepper. The stepper is the widest of them, only
 * adjusted now and then, and the menu and a pinch reach it anyway, so it waits for a title with room to spare
 * ([COMFORTABLE_TITLE_WIDTH]) beside the cover rather than squeezing either. The key chip and the tuner the bar holds
 * where the dock would hold nothing else ([playingToolsWidth], [appBarPlayingToolsWidth]) are always in the bar, so their
 * room is taken first and the rest goes into the menu sooner.
 */
internal fun appBarButtons(appBarWidth: Dp, hasCover: Boolean, isReadOnly: Boolean, playingToolsWidth: Dp = 0.dp): AppBarButtons {
    val fixedContentWidth = (if (isReadOnly) PERFORMANCE_FIXED_CONTENT_WIDTH else APP_BAR_FIXED_CONTENT_WIDTH) + playingToolsWidth
    val room = appBarWidth - fixedContentWidth - if (hasCover) APP_BAR_COVER_SIZE + APP_BAR_COVER_GAP else 0.dp
    val setlistAssignmentsWidth = if (isReadOnly) 0.dp else APP_BAR_OVERLAPPING_ACTION_WIDTH
    return AppBarButtons(
        isCoverShown = room >= MIN_TITLE_WIDTH,
        isSetlistAssignmentsShown = !isReadOnly && room - setlistAssignmentsWidth >= MIN_TITLE_WIDTH,
        isFontScaleShown = room - setlistAssignmentsWidth - FONT_SCALE_STEPPER_WIDTH >= COMFORTABLE_TITLE_WIDTH,
    )
}

/**
 * The room the bar keeps for the key chip ([hasKeyChip]) and the tuner's button ([hasTuner]) it holds in place of the
 * dock (`AppBarPlayingTools`). The chip is reserved by a constant rather than measured: what it says differs from page
 * to page (a key, its semitones, a capo), and the bar decides for every page at once, so a width that followed the page
 * would send the cover and the buttons into the menu and back as a setlist's pages turn. [KEY_CHIP_ROOM] holds a key
 * with its semitones ("F#m (+11)"); a chip that also names a capo takes the rest from the title, which is cut short.
 */
internal fun appBarPlayingToolsWidth(hasKeyChip: Boolean, hasTuner: Boolean) =
    (if (hasKeyChip) KEY_CHIP_ROOM else 0.dp) + if (hasTuner) APP_BAR_ACTION_WIDTH else 0.dp

/** A key chip naming a key and its semitones at the interface's text size, with the gaps parting it from its neighbours. */
private val KEY_CHIP_ROOM = 96.dp

private val APP_BAR_NAVIGATION_WIDTH = 52.dp // The 48dp button and the 4dp the bar pads its start by.
private val APP_BAR_END_PADDING = 4.dp
private val APP_BAR_ACTION_WIDTH = 48.dp

/** What one more button adds to a row of them, which reach into each other's touch targets (see [ACTION_BUTTON_OVERLAP]). */
private val APP_BAR_OVERLAPPING_ACTION_WIDTH = APP_BAR_ACTION_WIDTH - ACTION_BUTTON_OVERLAP

/** The back button, the bar's paddings and the overflow button. */
private val APP_BAR_FIXED_CONTENT_WIDTH = APP_BAR_NAVIGATION_WIDTH + APP_BAR_END_PADDING + APP_BAR_ACTION_WIDTH

/** What performance mode's bar always holds: the same, and the read only mark reaching into the overflow button. */
private val PERFORMANCE_FIXED_CONTENT_WIDTH = APP_BAR_FIXED_CONTENT_WIDTH + APP_BAR_OVERLAPPING_ACTION_WIDTH

private val MIN_TITLE_WIDTH = 160.dp // Enough of a title to tell which song is up.
private val COMFORTABLE_TITLE_WIDTH = 200.dp // Most titles whole, and the key beside the artist.
