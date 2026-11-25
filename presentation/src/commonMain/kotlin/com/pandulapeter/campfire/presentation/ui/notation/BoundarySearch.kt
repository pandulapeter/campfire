/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

/**
 * The search for the widest width a swing's tunes fit at ([NotationPlacement]): [fitting] is a width whose engraving the
 * layout placed no narrower than itself, [tooWide] one it placed narrower, and each [probe] halves the gap between them
 * as its engraving is placed ([record]). Which column a tune gets steps with its height, so a width just under the step
 * fits and one just over it does not; the search stops within [BOUNDARY_TOLERANCE] units of it, or after
 * [MAX_BOUNDARY_PROBES] engravings, each of which takes a run of the engraver, so it never engraves without end.
 */
internal class BoundarySearch(fitting: Int, tooWide: Int) {

    var fitting = fitting
        private set
    private var tooWide = tooWide
    private var probes = 0

    /** The width to engrave for next. */
    val probe get() = fitting + (tooWide - fitting) / 2

    val isDone get() = tooWide - fitting <= BOUNDARY_TOLERANCE || probes >= MAX_BOUNDARY_PROBES

    /** That the layout placed the [probe]'s engraving at [placed] units. */
    fun record(placed: Int) {
        val width = probe
        if (placed >= width) fitting = width else tooWide = width
        probes++
    }
}

/**
 * The search between the widest width of [swing] that [placedFor] says fit and the narrowest above it that did not, null
 * where the swing holds no such pair a step apart: the placements did not say what they were engraved for, or the two
 * are already as close as the search would bring them.
 */
internal fun boundarySearchOf(swing: List<Int>, placedFor: Map<Int, Int>): BoundarySearch? {
    val fitting = swing.filter { width -> placedFor[width]?.let { it >= width } == true }.maxOrNull() ?: return null
    val tooWide = swing.filter { width -> width > fitting && placedFor[width]?.let { it < width } == true }.minOrNull() ?: return null
    return BoundarySearch(fitting, tooWide).takeIf { !it.isDone }
}

/** How close, in SVG units, the search brings the fitting width to the one that does not fit before it stops. */
internal const val BOUNDARY_TOLERANCE = 4

/** The most engravings one search runs: enough to halve a gap as wide as the widest column down to the tolerance. */
internal const val MAX_BOUNDARY_PROBES = 7
