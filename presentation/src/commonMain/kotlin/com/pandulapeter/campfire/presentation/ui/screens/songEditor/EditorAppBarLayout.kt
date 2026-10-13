/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.APP_BAR_COVER_GAP
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.APP_BAR_COVER_SIZE

/**
 * How wide the editor's overflow menu ([EditorMenu]) may be in an app bar [appBarWidth] wide: what the close button,
 * undo, redo and Save leave beside a title of [MIN_TITLE_WIDTH], so that Find and Find and replace come out of the menu
 * as buttons of their own only where the song being edited can still be told by its name. The Shortcuts chevron counts
 * where the title row holds it ([isToolbarToggleShown]), and the cover wherever covers are drawn at all ([hasCover]),
 * rather than whether the text names one, since a menu that changed with every keystroke of an address would hand its
 * buttons back and forth while it is typed. Never less than the overflow button alone, which is always there.
 */
internal fun editorMenuMaxWidth(appBarWidth: Dp, isToolbarToggleShown: Boolean, hasCover: Boolean): Dp {
    val fixedWidth = APP_BAR_NAVIGATION_WIDTH + APP_BAR_END_PADDING + APP_BAR_ACTION_WIDTH + OVERLAPPING_ACTION_WIDTH * 2 +
        (if (isToolbarToggleShown) OVERLAPPING_ACTION_WIDTH else 0.dp) +
        (if (hasCover) APP_BAR_COVER_SIZE + APP_BAR_COVER_GAP else 0.dp)
    // The menu reaches into Save by the overlap, which its own width does not count.
    return maxOf(appBarWidth - fixedWidth - MIN_TITLE_WIDTH + ACTION_BUTTON_OVERLAP, APP_BAR_ACTION_WIDTH)
}

private val APP_BAR_NAVIGATION_WIDTH = 52.dp // The 48dp button and the 4dp the bar pads its start by.
private val APP_BAR_END_PADDING = 4.dp
private val APP_BAR_ACTION_WIDTH = 48.dp

/** What one more button adds to a row of them, which reach into each other's touch targets (see [ACTION_BUTTON_OVERLAP]). */
private val OVERLAPPING_ACTION_WIDTH = APP_BAR_ACTION_WIDTH - ACTION_BUTTON_OVERLAP

private val MIN_TITLE_WIDTH = 160.dp // Enough of a title to tell which song is being edited.
