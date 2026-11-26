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

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import com.pandulapeter.campfire.presentation.ui.songView.model.ScaleChipBlock

/**
 * What a page's dock draws in which order: its chord [cells] with the chords on the screen first, and its scale rows
 * by [groupOrder] ([dockScaleRowOrder]), both following the screen only as [rememberSettledForDockOrder] lets them,
 * while the dimming follows it at once ([chordsOnScreen], [groupsOnScreen]). [scaleWidths] are the scale rows' drawn
 * widths, by which the row scrolls past a dimmed home group to the first group on the screen.
 */
@Stable
internal class DockRowsOrder(
    val cells: List<ChordCell>,
    val chordsOnScreen: Set<String>,
    val groupsOnScreen: Set<Int>?,
    val groupOrder: Set<Int>?,
    val scaleWidths: ScaleRowWidths,
)

/** The drawn widths of a dock's scale rows' titles and chips, by row and place in it. Not a state: read only to scroll. */
internal class ScaleRowWidths {
    private val widths = mutableMapOf<Pair<ScaleChipBlock, Int>, Int>()

    fun record(row: ScaleChipBlock, index: Int, width: Int) {
        widths[row to index] = width
    }

    /** How far the rows of the groups [isHome] says run, each element with the [gap] after it, in pixels. */
    fun widthOf(isHome: (Int) -> Boolean, gap: Int) = widths.entries.filter { isHome(it.key.first.groupIndex) }.sumOf { it.value + gap }
}

/**
 * [DockRowsOrder] for a page's [content], the screen [stepper] shows, and its chord [cells], the order waiting while
 * [isHeld].
 */
@Composable
internal fun rememberDockRowsOrder(content: DockContent?, stepper: SongStepper?, cells: List<ChordCell>, isHeld: () -> Boolean): DockRowsOrder {
    val names = remember(cells) { cells.mapTo(mutableSetOf()) { it.name } }
    val chordsOnScreen = rememberChordsOnScreen(stepper, content?.model?.sections.orEmpty(), names)
    val groupsOnScreen = rememberScaleGroupsOnScreen(stepper, content?.model?.scaleGroups.orEmpty())
    val chordOrder = rememberSettledForDockOrder(chordsOnScreen, isHeld)
    val groupOrder = rememberSettledForDockOrder(groupsOnScreen, isHeld)
    val orderedCells = remember(cells, chordOrder) { dockCellOrder(cells, chordOrder) }
    val scaleWidths = remember { ScaleRowWidths() }
    return DockRowsOrder(orderedCells, chordsOnScreen, groupsOnScreen, groupOrder, scaleWidths)
}

/**
 * Scrolls [scrollState] to [target] (in pixels, read once the new order is laid out) whenever [order] changes after the
 * first frame: the row brought back to what the reader is now playing, its first item on the screen.
 */
@Composable
internal fun ScrollWithDockOrder(order: Any?, scrollState: ScrollState, target: () -> Int) {
    val last = remember { LastOrder() }
    LaunchedEffect(order) {
        val isFirst = !last.isSet
        last.isSet = true
        if (isFirst) return@LaunchedEffect
        // The new order is placed in the frame after the one it was composed in.
        withFrameNanos { }
        scrollState.animateScrollTo(target().coerceIn(0, scrollState.maxValue))
    }
}

/**
 * Where the scale row ([order]'s) scrolls to after a reorder: its start, unless the home group, which keeps its place
 * first, is dimmed while another group is on the screen, when it is that group's first row, past the home group's.
 */
@Composable
internal fun scaleRowTarget(order: DockRowsOrder, content: DockContent?): () -> Int {
    val gap = with(LocalDensity.current) { DOCK_ITEM_GAP.roundToPx() }
    return {
        val groups = content?.model?.scaleGroups.orEmpty()
        val groupOrder = order.groupOrder
        val homeIndex = groups.indexOfFirst { it.isHome }
        if (groupOrder == null || homeIndex < 0 || homeIndex in groupOrder) 0 else order.scaleWidths.widthOf({ it == homeIndex }, gap)
    }
}

/** Whether [ScrollWithDockOrder] has seen its first order. Not a state. */
private class LastOrder(var isSet: Boolean = false)
