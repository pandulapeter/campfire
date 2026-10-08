/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * The scrolling body of one tab of the settings screen: its [section], and the [secondSection] where it has one, side
 * by side where the window has room for both at [MIN_COLUMN_WIDTH] and stacked in that order where it does not, the
 * columns starting at the start edge and stopping at [MAX_COLUMN_WIDTH] so that a maximized window does not stretch a
 * switch a meter away from its label.
 *
 * A tab holds no more than two sections, so the columns are either all of them or one: a section never moves to
 * another column because the one above it grew, which is what would happen to a staggered grid when the sync section
 * changes height.
 *
 * It is a plain scrolling layout rather than a lazy list. A lazy list has no notion of a section that holds several
 * rows, a tab is a few dozen rows that are cheap to keep composed, and rows that come and go inside a section
 * ([AnimatedSettingsRow]) are first composed in the state they are in - where an item added to a lazy list a frame
 * late is animated in, which is a screen rearranging itself while it is still arriving.
 *
 * @param sectionColumns How many [MIN_COLUMN_WIDTH] wide columns the page has room for, see [SettingsWidthLayout].
 * @param contentPadding The window insets left to the screen, applied inside the scroll so the rows pass under the
 *   system bars instead of stopping short of them.
 */
@Composable
internal fun SettingsPage(
    modifier: Modifier = Modifier,
    sectionColumns: Int,
    scrollState: ScrollState,
    contentPadding: PaddingValues,
    section: @Composable () -> Unit,
    secondSection: (@Composable () -> Unit)? = null,
) {
    val sections = listOfNotNull(section, secondSection)
    val columns = if (sectionColumns >= sections.size) sections.map { listOf(it) } else listOf(sections)
    Row(
        modifier = modifier
            .fillMaxSize()
            .fadingTopEdge(scrollState, MaterialTheme.colorScheme.background)
            .bounceVerticalScroll(scrollState)
            // Asked while measuring rather than while composing, since the bottom follows the keyboard of a sheet's field.
            .padding(contentPadding.only(start = true, end = true, bottom = true, extraTop = PAGE_TOP_PADDING, extraBottom = 16.dp)),
        horizontalArrangement = Arrangement.spacedBy(SECTION_GAP, Alignment.Start),
    ) {
        columns.forEach { column ->
            Column(
                // Not filling its share, or the share would win over the maximum width and the columns of a wide
                // window would never stop growing; the sections fill whatever this settles at.
                modifier = Modifier.weight(1f, fill = false).widthIn(max = MAX_COLUMN_WIDTH),
                verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
            ) {
                column.forEach { section -> section() }
            }
        }
    }
}
