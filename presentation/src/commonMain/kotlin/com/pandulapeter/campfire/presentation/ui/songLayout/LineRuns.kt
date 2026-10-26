/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songLayout

import com.pandulapeter.campfire.chordpro.model.ChordProLine

internal fun ChordProLine.foldableKind() = when (this) {
    is ChordProLine.Tab -> FoldableKind.TAB
    is ChordProLine.Grid -> FoldableKind.GRID
    is ChordProLine.Lyrics, is ChordProLine.Verbatim, ChordProLine.Blank -> null
}

/**
 * How many pieces a run of tablature or grid lines is drawn as, which the section may be cut between: a line of a grid
 * each, and for tablature as many as the rows it could wrap into (see `SongTabBlock`), which are only known once the
 * width is - a slot for every line and for every [MIN_TAB_SLOT_COLUMNS] characters of the longest, which no real width
 * wraps it into more rows than, and never more than [MAX_TAB_SLOTS], the last one holding whatever rows are left. A slot
 * with no row at a width has no height there, and nothing is cut in front of it.
 */
internal fun List<ChordProLine>.runSlotCount(kind: FoldableKind) = when (kind) {
    FoldableKind.TAB -> (size + maxOf { (it as? ChordProLine.Tab)?.text?.length ?: 0 } / MIN_TAB_SLOT_COLUMNS).coerceIn(1, MAX_TAB_SLOTS)
    FoldableKind.GRID -> count { it is ChordProLine.Grid }.coerceAtLeast(1)
}

/** The kind a section is written in from start to end, which is then folded as a whole, or null for any other. */
internal fun List<ChordProLine>.wholeFoldableKind() = when {
    areAll<ChordProLine.Tab>() -> FoldableKind.TAB
    areAll<ChordProLine.Grid>() -> FoldableKind.GRID
    else -> null
}

/** What the `{start_of_tab}` or `{start_of_grid}` a line was written in was labelled, which names its fold. */
internal val ChordProLine.environmentLabel
    get() = when (this) {
        is ChordProLine.Tab -> label
        is ChordProLine.Grid -> label
        is ChordProLine.Lyrics, is ChordProLine.Verbatim, ChordProLine.Blank -> null
    }

/**
 * Splits lines into runs of tablature, runs of grid lines and runs of everything else, keeping their order. Each of
 * the first two is folded as one, and tablature is also measured and scrolled as a block, so the lines of a run have to
 * reach the layout together. Two environments written one after the other are two runs where they were labelled
 * differently, since each is then folded under its own name.
 */
internal fun List<ChordProLine>.groupIntoRuns(): List<List<ChordProLine>> {
    val groups = mutableListOf<List<ChordProLine>>()
    var group = mutableListOf<ChordProLine>()
    forEach { line ->
        val first = group.firstOrNull()
        if (first != null && (first.foldableKind() != line.foldableKind() || first.environmentLabel != line.environmentLabel)) {
            groups += group
            group = mutableListOf()
        }
        group += line
    }
    if (group.isNotEmpty()) groups += group
    return groups
}

private const val MIN_TAB_SLOT_COLUMNS = 12
private const val MAX_TAB_SLOTS = 24
