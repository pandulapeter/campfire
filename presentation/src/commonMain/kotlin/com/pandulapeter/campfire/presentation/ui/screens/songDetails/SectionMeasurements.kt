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

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Constraints

/**
 * What [SongSectionsLayout] has already worked out about one set of sections: the [sizes] of each, which outlive it for
 * the sections a change leaves as they were (see [SectionSizesPool]), and the grid last decided for them. None of it
 * is state: it is read and written by the measurement alone, and a change to it never has anything to redraw. The
 * heights are kept per width and only for the widths of the last few searches.
 */
internal class SectionMeasurements(private val sizes: List<SectionSizes>) {

    private var lastGridKey: SectionGridKey? = null
    private var lastGrid = DecidedGrid(emptyGrid(), isInset = false)

    /** The intrinsic height of the section at [index] when it is [width] wide. */
    fun height(index: Int, width: Int, measure: (Int) -> Int): Int = sizes[index].heightsByWidth.getOrPut(width) { measure(width) }

    /** The minimum intrinsic width of the section at [index], which no width changes. */
    fun minWidth(index: Int, measure: (Int) -> Int): Int {
        val sectionSizes = sizes[index]
        if (sectionSizes.minWidth == UNMEASURED) sectionSizes.minWidth = measure(Constraints.Infinity)
        return sectionSizes.minWidth
    }

    /** The maximum intrinsic width of the section at [index], which is how wide a card around it grows. */
    fun maxWidth(index: Int, measure: (Int) -> Int): Int {
        val sectionSizes = sizes[index]
        if (sectionSizes.maxWidth == UNMEASURED) sectionSizes.maxWidth = measure(Constraints.Infinity)
        return sectionSizes.maxWidth
    }

    /** The grid decided for [key], which is only searched for again once the key has changed. */
    fun grid(key: SectionGridKey, search: () -> DecidedGrid): DecidedGrid {
        if (key != lastGridKey) {
            // A window being resized searches at a new width on every frame and none of those comes back, so
            // the widths are only kept until there are more of them than a few searches ask about. They are let
            // go of between two searches and never during one, which asks about the same few over and over.
            sizes.forEach { if (it.heightsByWidth.size > MAX_SECTION_WIDTHS) it.heightsByWidth.clear() }
            lastGrid = search()
            lastGridKey = key
        }
        return lastGrid
    }
}

/** What one section measures, kept for as long as a section equal to it is on the page. */
internal class SectionSizes {

    val heightsByWidth = HashMap<Int, Int>()
    var minWidth = UNMEASURED
    var maxWidth = UNMEASURED
}

/**
 * Hands out the [SectionSizes] of a list of sections, reusing those of the sections the previous list held by content:
 * a section equal to one before measures the same at every width, since nothing outside the section decides its size
 * that the pool is not remembered by. Equal sections take the sizes in the order they come in; a section that is new
 * starts with nothing measured.
 */
internal class SectionSizesPool<T> {

    private var sizesBySection = HashMap<T, ArrayDeque<SectionSizes>>()

    fun sizesFor(sections: List<T>): List<SectionSizes> {
        val next = HashMap<T, ArrayDeque<SectionSizes>>()
        val sizes = sections.map { section ->
            (sizesBySection[section]?.removeFirstOrNull() ?: SectionSizes()).also { next.getOrPut(section) { ArrayDeque() }.addLast(it) }
        }
        sizesBySection = next
        return sizes
    }
}

/** Everything the [SectionGrid] depends on, apart from the heights of the sections. */
internal data class SectionGridKey(
    val settledWidth: Int,
    val availableHeight: Int,
    val maxRowHeight: Int,
    val maxColumnCount: Int,
    val endInset: Int,
    val keepsEndInset: Boolean,
)

/** The grid [SongSectionsLayout] decided on, and whether it was decided for the width less the end inset. */
internal data class DecidedGrid(
    val grid: SectionGrid,
    val isInset: Boolean,
) {

    /** Whether [other] lays every section out in the same cell and in the same width as this one. */
    fun hasSameCellsAs(other: DecidedGrid) = isInset == other.isInset && grid.hasSameCellsAs(other.grid)
}

private const val MAX_SECTION_WIDTHS = 32

private const val UNMEASURED = -1
