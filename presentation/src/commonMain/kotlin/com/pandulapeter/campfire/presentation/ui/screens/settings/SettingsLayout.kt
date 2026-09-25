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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge

/**
 * The tabs of the settings screen, in the order they are read in: what the app looks like and lets its user do, then
 * what a song is read with, then where the library is and where else it goes, and what the app is last. General comes
 * first, and is what the screen opens on, because performance mode is its first row and that switch decides what the
 * rest of the app is still allowed to do - it is also the only way back out of the mode, so it must never be
 * something to go looking for.
 */
internal enum class SettingsTab {
    GENERAL,
    SONGS,
    LIBRARY,
    ABOUT,
}

/**
 * The tabs of the settings screen, which stay where they are while a page scrolls under them.
 *
 * The tabs are capped at [SETTINGS_TAB_ROW_MAX_WIDTH] and start where every list of the app starts - four tabs spread across a
 * wide window are four words a hand's width apart.
 *
 * @param badgedTab A tab holding something that waits for an answer, marked with a dot so that it is found from the
 *   others. The dot rather than the tab opening itself, which would be the screen moving under a reader's finger.
 * @param startPadding The window insets on the start edge, so the tabs start after the cutout rather than under it.
 */
@Composable
internal fun SettingsTabRow(
    modifier: Modifier = Modifier,
    selectedTab: SettingsTab,
    badgedTab: SettingsTab?,
    label: @Composable (SettingsTab) -> String,
    startPadding: Dp,
    endPadding: Dp,
    onTabSelected: (SettingsTab) -> Unit,
) = Column(modifier = modifier.fillMaxWidth()) {
    PrimaryTabRow(
        modifier = Modifier
            .padding(start = startPadding, end = endPadding)
            .widthIn(max = SETTINGS_TAB_ROW_MAX_WIDTH),
        selectedTabIndex = selectedTab.ordinal,
        containerColor = Color.Transparent,
        divider = {},
    ) {
        SettingsTab.entries.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    BadgedBox(badge = { if (tab == badgedTab) Badge() }) {
                        Text(text = label(tab), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
            )
        }
    }
}

/**
 * The tabs of the settings screen as a list at the start of the screen, for a window wide enough to hold the selected
 * page next to it: the way a tab is chosen where a row of tabs would be four words spread across the window. It is
 * [SETTINGS_CATEGORY_PANE_WIDTH] wide, so that the page beside it can be told how much room it has left.
 *
 * @param badgedTab A tab holding something that waits for an answer, marked with a dot the way [SettingsTabRow] does.
 */
@Composable
internal fun SettingsCategoryPane(
    modifier: Modifier = Modifier,
    selectedTab: SettingsTab,
    badgedTab: SettingsTab?,
    label: @Composable (SettingsTab) -> String,
    onTabSelected: (SettingsTab) -> Unit,
) = Column(
    modifier = modifier.width(SETTINGS_CATEGORY_PANE_WIDTH).padding(horizontal = 12.dp, vertical = PAGE_TOP_PADDING),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    SettingsTab.entries.forEach { tab ->
        NavigationDrawerItem(
            label = { Text(text = label(tab), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            selected = tab == selectedTab,
            onClick = { onTabSelected(tab) },
            badge = if (tab == badgedTab) ({ Badge() }) else null,
        )
    }
}

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
 * @param settledWidth The width the screen settles at without its insets, rather than the one it is being measured at,
 *   which follows the navigation chrome while that animates. It is what the number of columns is decided from.
 * @param contentPadding The window insets left to the screen, applied inside the scroll so the rows pass under the
 *   system bars instead of stopping short of them.
 */
@Composable
internal fun SettingsPage(
    modifier: Modifier = Modifier,
    settledWidth: Dp,
    scrollState: ScrollState,
    contentPadding: PaddingValues,
    section: @Composable () -> Unit,
    secondSection: (@Composable () -> Unit)? = null,
) {
    val layoutDirection = LocalLayoutDirection.current
    val sections = listOfNotNull(section, secondSection)
    val columns = if ((settledWidth / MIN_COLUMN_WIDTH).toInt() >= sections.size) sections.map { listOf(it) } else listOf(sections)
    Row(
        modifier = modifier
            .fillMaxSize()
            .fadingTopEdge(scrollState)
            .verticalScroll(scrollState)
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = PAGE_TOP_PADDING,
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
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

/**
 * One group of settings: its rows, under a [title] where the tab holds another group as well. A tab holding one names
 * it already, so that one has none.
 *
 * The rows lie on the screen itself rather than on a card of their own, so a row's ripple and its text keep the
 * keylines of the app bar and of every list in the app; what tells two groups apart is the title and the room above it.
 */
@Composable
internal fun SettingsSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Column(modifier = modifier.fillMaxWidth()) {
    title?.let {
        Text(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = SECTION_TITLE_PADDING),
            text = it,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    content()
}

/**
 * A paragraph inside a [SettingsSection] that belongs to the section rather than to one of its rows: what a section is
 * for, or what went wrong in it.
 */
@Composable
internal fun SettingsMessage(
    modifier: Modifier = Modifier,
    text: String,
) = Text(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * A setting whose control does not fit at the end of a row - a segmented choice, the color discs, a list of radio
 * buttons - and so goes under its name instead. The name and the description are set the way a list item sets its
 * headline and supporting text, since that is what they are: the row next to it is a switch with the same two lines.
 *
 * @param isEnabled Dims the title and the description the way a disabled row is dimmed; the control inside is left
 *   to disable itself, so that it is dimmed once rather than twice.
 */
@Composable
internal fun SettingsSubsection(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    isEnabled: Boolean = true,
    shouldApplyPadding: Boolean = true,
    content: @Composable () -> Unit,
) = Column(modifier = modifier.padding(vertical = if (shouldApplyPadding) SUBSECTION_PADDING else 0.dp)) {
    val labelAlpha = if (isEnabled) 1f else 0.5f
    title?.let {
        Text(
            modifier = Modifier.alpha(labelAlpha).padding(horizontal = 16.dp),
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    description?.let {
        Text(
            modifier = Modifier.alpha(labelAlpha).padding(horizontal = 16.dp),
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(modifier = Modifier.padding(top = SUBSECTION_CONTROL_GAP)) { content() }
}

/**
 * A row of a [SettingsSection] that is only there while there is a [value] to draw it from, expanding into the section
 * and shrinking out of it so that the rows and the section under it move rather than jump.
 *
 * The last value is kept after it has gone, since the row still has to be drawn while it shrinks away and what it
 * showed - an account, a progress - is exactly what is no longer there. A row whose value is there from the start is
 * composed at its full height, with nothing animating: only a change is narrated, never an arrival.
 */
@Composable
internal fun <T : Any> ColumnScope.AnimatedSettingsRow(
    value: T?,
    content: @Composable (T) -> Unit,
) {
    val lastValue = remember { LastValue(value) }
    value?.let { lastValue.value = it }
    AnimatedVisibility(
        visible = value != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        lastValue.value?.let { content(it) }
    }
}

/** The [AnimatedSettingsRow] of a row that needs nothing but to know whether it is there. */
@Composable
internal fun ColumnScope.AnimatedSettingsRow(
    isVisible: Boolean,
    content: @Composable () -> Unit,
) = AnimatedSettingsRow(value = Unit.takeIf { isVisible }) { content() }

/** What an [AnimatedSettingsRow] draws while it leaves. Not a state, since nothing is ever redrawn because of it. */
private class LastValue<T>(var value: T?)

/** Leaves a full [MAX_COLUMN_WIDTH] for a page as soon as the tab row reaches its width cap. */
internal val SETTINGS_CATEGORY_PANE_WIDTH = 180.dp

/** Below this a column is too narrow for a switch next to two lines of description, so a tab stacks its sections. */
private val MIN_COLUMN_WIDTH = 380.dp

/**
 * Above this a row is mostly the distance between its label and its control. It is as wide as every theme color disc
 * in one row - ten, where the system hands out a palette of its own - with their gaps and the row's padding, so that the
 * color choice does not wrap wherever it has the room.
 */
private val MAX_COLUMN_WIDTH = 664.dp

/** The room between two sections, whether they are side by side or one above the other. */
private val SECTION_GAP = 16.dp

/** What the first row of a page keeps free under the tabs. */
private val PAGE_TOP_PADDING = 8.dp

/** What the title of a section keeps above and below itself. */
private val SECTION_TITLE_PADDING = 8.dp

/** Above this four tabs are four words spread apart rather than a row of tabs. */
internal val SETTINGS_TAB_ROW_MAX_WIDTH = MAX_COLUMN_WIDTH

/** The room a [SettingsSubsection] keeps above and below itself, which is what a list item pads itself by. */
private val SUBSECTION_PADDING = 12.dp

/** The gap between the text of a [SettingsSubsection] and its control. */
private val SUBSECTION_CONTROL_GAP = 12.dp
