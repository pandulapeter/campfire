/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.settingsPage

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.spatialBoundsSpec
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsTabPage

/**
 * The scrolling body of one tab of the settings screen: its [section], and the [secondSection] where it has one, side
 * by side where the window has room for both at [MIN_COLUMN_WIDTH] and stacked in that order where it does not, the
 * columns starting at the start edge and stopping at [MAX_COLUMN_WIDTH] so that a maximized window does not stretch a
 * switch a meter away from its label.
 *
 * A page holds no more than two sections, so the columns are either all of them or one: a section never moves to
 * another column because the one above it grew, which is what would happen to a staggered grid when a section changes
 * height. The settings tabs pass one column whatever the width (see [SettingsTabPage]); the Metronome and the Tuner,
 * whose two sections are both about the one instrument on screen, are the pages that spread out. A window resized
 * across the width that decides it slides the sections from one arrangement to the other, the header's width following
 * on the same spring, rather than having them jump to their new places.
 *
 * It is a plain scrolling layout rather than a lazy list. A lazy list has no notion of a section that holds several
 * rows, a tab is a few dozen rows that are cheap to keep composed, and rows that come and go inside a section
 * ([AnimatedSettingsRow]) are first composed in the state they are in - where an item added to a lazy list a frame
 * late is animated in, which is a screen rearranging itself while it is still arriving.
 *
 * @param sectionColumns How many [MIN_COLUMN_WIDTH] wide columns the page has room for, see [PageWidthLayout].
 * @param contentPadding The window insets left to the screen, applied inside the scroll so the rows pass under the
 *   system bars instead of stopping short of them.
 * @param header Rows above the sections that span every column, so that a control which reads as one row (the Tuner
 *   tab's strings) is not cut to the width of one column of a wide window.
 * @param section Left out by a page that is all header (the Tuner tab).
 * @param headerColumns How many columns the header spans where the window has room for them, for a page whose header
 *   lines up with something wider than its sections (the Tuner tab's display); as many as the sections take otherwise.
 */
@Composable
internal fun SettingsPage(
    modifier: Modifier = Modifier,
    sectionColumns: Int,
    scrollState: ScrollState,
    contentPadding: PaddingValues,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    headerColumns: Int = 1,
    section: (@Composable () -> Unit)? = null,
    secondSection: (@Composable () -> Unit)? = null,
) {
    val sections = listOfNotNull(section, secondSection)
    val columnCount = if (sectionColumns >= sections.size) sections.size else 1
    Column(
        modifier = modifier
            .fillMaxSize()
            .fadingTopEdge(scrollState, MaterialTheme.colorScheme.background)
            .bounceVerticalScroll(scrollState)
            // Asked while measuring rather than while composing, since the bottom follows the keyboard of a sheet's field.
            .padding(contentPadding.only(start = true, end = true, bottom = true, extraTop = PAGE_TOP_PADDING, extraBottom = 16.dp)),
    ) {
        if (header != null) {
            val headerSpan = maxOf(columnCount, minOf(headerColumns, sectionColumns), 1)
            val headerMaxWidth by animateDpAsState(
                targetValue = MAX_COLUMN_WIDTH * headerSpan + SECTION_GAP * (headerSpan - 1),
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            )
            Column(
                // As wide as the columns under it at most, so that it ends where they do.
                modifier = Modifier.widthIn(max = headerMaxWidth).fillMaxWidth(),
                content = header,
            )
        }
        SectionColumns(sections = sections, columnCount = columnCount)
    }
}

/**
 * The sections of a [SettingsPage], side by side in [columnCount] columns of at most [MAX_COLUMN_WIDTH] or stacked in
 * one. Each section is a child of this one layout in either arrangement, rather than of a `Row` or of the `Column` of
 * its column, so that it keeps its state and its bounds across a change of arrangement and slides to its new place and
 * width (`animateBounds`) - only then, since a resize within one arrangement is followed as it comes.
 */
@Composable
private fun SectionColumns(
    sections: List<@Composable () -> Unit>,
    columnCount: Int,
) {
    val arrangement = updateTransition(targetState = columnCount, label = "SettingsPageColumns")
    val motionScheme = MaterialTheme.motionScheme
    // A child animation on the same spring keeps the transition running for as long as the sections travel, which is what
    // tells a change of arrangement from a resize in the frames after the first one.
    arrangement.animateFloat(transitionSpec = { motionScheme.defaultSpatialSpec() }, label = "SettingsPageColumnsProgress") {
        it.toFloat()
    }
    val spec = motionScheme.spatialBoundsSpec()
    val boundsTransform = remember(arrangement, spec) {
        BoundsTransform { _, _ -> if (arrangement.currentState != arrangement.targetState) spec else snap() }
    }
    LookaheadScope {
        val sectionBounds = Modifier.animateBounds(lookaheadScope = this, boundsTransform = boundsTransform)
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = { sections.forEach { section -> Box(modifier = sectionBounds) { section() } } },
        ) { measurables, constraints ->
            val gap = SECTION_GAP.roundToPx()
            val columnWidth = minOf(MAX_COLUMN_WIDTH.roundToPx(), (constraints.maxWidth - gap * (columnCount - 1)) / columnCount).coerceAtLeast(0)
            val placeables = measurables.map { it.measure(Constraints(maxWidth = columnWidth)) }
            val height = if (columnCount > 1) {
                placeables.maxOfOrNull { it.height } ?: 0
            } else {
                placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
            }
            layout(constraints.maxWidth, height) {
                var x = 0
                var y = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    if (columnCount > 1) x += columnWidth + gap else y += placeable.height + gap
                }
            }
        }
    }
}
