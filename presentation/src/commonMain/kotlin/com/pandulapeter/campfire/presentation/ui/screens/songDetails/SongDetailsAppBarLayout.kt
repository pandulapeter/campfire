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
import com.pandulapeter.campfire.presentation.ui.components.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.STEPPER_WIDTH

/**
 * Whether the app bar of a screen [appBarWidth] wide has room for the text size stepper in performance mode, where it is
 * the only control the bar holds: wherever it leaves the title [MIN_TITLE_WIDTH], since the title is what tells the
 * player which song is up. Where it does not, the stepper is in a menu of its own. The cover is not counted, since it
 * is the first to leave ([showsCoverInPerformanceMode]).
 */
internal fun showsFontScaleInPerformanceBar(appBarWidth: Dp) = appBarWidth - APP_BAR_NAVIGATION_WIDTH - APP_BAR_END_PADDING -
    STEPPER_WIDTH - APP_BAR_STEPPER_END_PADDING >= MIN_TITLE_WIDTH

/**
 * Whether the app bar of a screen [appBarWidth] wide in performance mode still has room for the metronome button next to
 * the text size stepper ([showsFontScaleInPerformanceBar]), leaving the title [MIN_TITLE_WIDTH]. It goes into the menu
 * before the stepper does: the stepper is pinched as often as it is tapped, the button is tapped once.
 */
internal fun showsMetronomeInPerformanceBar(appBarWidth: Dp) = showsFontScaleInPerformanceBar(appBarWidth) &&
    appBarWidth - APP_BAR_NAVIGATION_WIDTH - APP_BAR_END_PADDING - STEPPER_WIDTH - APP_BAR_STEPPER_END_PADDING -
    APP_BAR_ACTION_WIDTH >= MIN_TITLE_WIDTH

/**
 * Whether the app bar of a screen [appBarWidth] wide still has room for the cover in performance mode. The cover is
 * decoration there, so it is only shown next to the text size stepper and the metronome button
 * ([showsMetronomeInPerformanceBar]) and only where the title is still left [MIN_TITLE_WIDTH] beside all three.
 */
internal fun showsCoverInPerformanceMode(appBarWidth: Dp) = showsMetronomeInPerformanceBar(appBarWidth) &&
    appBarWidth - APP_BAR_NAVIGATION_WIDTH - APP_BAR_END_PADDING - STEPPER_WIDTH - APP_BAR_STEPPER_END_PADDING -
    APP_BAR_ACTION_WIDTH - APP_BAR_COVER_SIZE - APP_BAR_COVER_GAP >= MIN_TITLE_WIDTH

/** What the song details app bar has the room for outside read only mode, see [appBarButtons]. */
internal data class AppBarButtons(
    val isCoverShown: Boolean,
    val isSetlistAssignmentsShown: Boolean,
)

/**
 * What the app bar of a screen [appBarWidth] wide has the room for besides what it always holds: the back button, the
 * metronome, the editing menu and the overflow button ([APP_BAR_FIXED_CONTENT_WIDTH]), which stay whatever the width,
 * since the click and the way into the editor are what the screen is opened for. The rest leaves one at a time as the
 * bar narrows, each needing the title left [MIN_TITLE_WIDTH]: the setlist assignments first, into the overflow menu,
 * and then the cover in front of the title, which only decorates it - where [hasCover] says no song of the pager has
 * one, it takes no room. The setlist assignments are only there while the cover is.
 */
internal fun appBarButtons(appBarWidth: Dp, hasCover: Boolean): AppBarButtons {
    val room = appBarWidth - APP_BAR_FIXED_CONTENT_WIDTH - if (hasCover) APP_BAR_COVER_SIZE + APP_BAR_COVER_GAP else 0.dp
    return AppBarButtons(
        isCoverShown = room >= MIN_TITLE_WIDTH,
        isSetlistAssignmentsShown = room - APP_BAR_OVERLAPPING_ACTION_WIDTH >= MIN_TITLE_WIDTH,
    )
}

/**
 * What the text size stepper of performance mode leaves after it, the last thing in the bar. The transposition stepper
 * has none: a button always follows it, and that button's touch target already keeps its icon off the pill by as much
 * as two neighboring buttons keep their icons apart.
 */
internal val APP_BAR_STEPPER_END_PADDING = 8.dp

private val APP_BAR_NAVIGATION_WIDTH = 52.dp // The 48dp button and the 4dp the bar pads its start by.
private val APP_BAR_END_PADDING = 4.dp
private val APP_BAR_ACTION_WIDTH = 48.dp

/** What one more button adds to a row of them, which reach into each other's touch targets (see [ACTION_BUTTON_OVERLAP]). */
private val APP_BAR_OVERLAPPING_ACTION_WIDTH = APP_BAR_ACTION_WIDTH - ACTION_BUTTON_OVERLAP

/** The back button, the bar's paddings, the metronome, the editing menu and the overflow button. */
private val APP_BAR_FIXED_CONTENT_WIDTH = APP_BAR_NAVIGATION_WIDTH + APP_BAR_END_PADDING + APP_BAR_ACTION_WIDTH +
    APP_BAR_OVERLAPPING_ACTION_WIDTH * 2

/** As tall as the title and the artist next to it: a titleMedium and a bodySmall line. The editor's bar shares it. */
internal val APP_BAR_COVER_SIZE = 40.dp
internal val APP_BAR_COVER_GAP = 12.dp

private val MIN_TITLE_WIDTH = 160.dp // Enough of a title to tell which song is up.
