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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * The side panel's diagrams wrapped into lines [gap] apart both ways, each item centred in its line's height, the block
 * as wide as its widest line ([dockPanelGridOf] places them).
 */
@Composable
internal fun DockPanelGrid(
    modifier: Modifier = Modifier,
    gap: Dp,
    content: @Composable () -> Unit,
) = Layout(modifier = modifier, content = content) { measurables, constraints ->
    val gapPx = gap.roundToPx()
    val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
    val grid = dockPanelGridOf(widths = placeables.map { it.width }, maxWidth = constraints.maxWidth, gap = gapPx)
    val lineHeights = grid.lines.map { line -> line.maxOf { placeables[it.index].height } }
    val height = lineHeights.sum() + gapPx * (lineHeights.size - 1).coerceAtLeast(0)
    layout(grid.width, height) {
        var y = 0
        grid.lines.forEachIndexed { lineIndex, line ->
            line.forEach { item ->
                val placeable = placeables[item.index]
                placeable.placeRelative(item.x, y + (lineHeights[lineIndex] - placeable.height) / 2)
            }
            y += lineHeights[lineIndex] + gapPx
        }
    }
}

/** Where [dockPanelGridOf] puts the items: its [lines] in order, and the block's [width]. */
internal data class DockPanelGridPlacement(
    val width: Int,
    val lines: List<List<Item>>,
) {
    /** The item at [index] of the content, [x] from the block's start. */
    data class Item(
        val index: Int,
        val x: Int,
    )
}

/**
 * Wraps items of [widths] into lines no wider than [maxWidth], [gap] apart, as wide as the widest line. A line of
 * several starts at the block's start, so the diagrams read as columns. An item alone on its line is centred, as a
 * chord whose long name keeps it from sharing a line would otherwise hang at one side of the panel, except an odd last
 * chord no wider than the one above it, which stays in that column rather than standing apart from the grid.
 */
internal fun dockPanelGridOf(widths: List<Int>, maxWidth: Int, gap: Int): DockPanelGridPlacement {
    val lines = mutableListOf<MutableList<Int>>()
    var lineWidth = 0
    widths.forEachIndexed { index, width ->
        val line = lines.lastOrNull()
        if (line != null && lineWidth + gap + width <= maxWidth) {
            line += index
            lineWidth += gap + width
        } else {
            lines += mutableListOf(index)
            lineWidth = width
        }
    }
    val lineWidths = lines.map { line -> line.sumOf { widths[it] } + gap * (line.size - 1) }
    val blockWidth = lineWidths.maxOrNull() ?: 0
    return DockPanelGridPlacement(
        width = blockWidth,
        lines = lines.mapIndexed { lineIndex, line ->
            val above = lines.getOrNull(lineIndex - 1)
            val staysInColumn = line.size > 1 ||
                (lineIndex == lines.lastIndex && above != null && above.size > 1 && widths[line[0]] <= widths[above[0]])
            var x = if (staysInColumn) 0 else (blockWidth - lineWidths[lineIndex]) / 2
            line.map { index ->
                DockPanelGridPlacement.Item(index = index, x = x).also { x += widths[index] + gap }
            }
        },
    )
}
