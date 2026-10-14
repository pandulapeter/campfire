/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import com.pandulapeter.campfire.presentation.ui.songLayout.runSlotCount

/** What one of the things a section is drawn as, one under the other, is to where the section may be cut. */
internal enum class SectionItemKind {

    /** A line of lyrics, with or without its chords. */
    CONTENT,

    /**
     * A piece of a run of tablature or grid lines: a line of a grid, or a slot of the rows a staff of tablature wraps
     * into (see [runSlotCount]). It is a line to [sectionChunkStarts] as [CONTENT] is, and kept apart from it because it
     * is no line of lyrics, which is what a piece left with too few of them is counted in (see [SongUnits.lineCount]).
     */
    RUN,

    /** An empty line, which says where a stanza ends. */
    BLANK,

    /** A comment standing between two lines, which is read with the line after it. */
    COMMENT,
}

/**
 * Where a section of items of [kinds] may be cut, as the index of the item every chunk starts with (the first one is
 * always 0). A section is cut wherever that fills a column or a page further, since a page saved is worth more than a
 * section kept in one piece, and wherever it may be cut it is composed and measured as separate chunks, which the layout
 * keeps together until it cuts between two of them.
 *
 * A piece holds at least one line on either side of a cut - a line of lyrics with its chords, never the chords alone,
 * or a piece of a run of tablature or grid lines - and the first piece starts with the section's header. A comment is
 * never left at the end of a piece, since it introduces what follows it, and an empty line never starts one, where it
 * would only push the piece down from the top of its column.
 */
internal fun sectionChunkStarts(kinds: List<SectionItemKind>): IntArray {
    val contentBefore = IntArray(kinds.size + 1)
    kinds.forEachIndexed { index, kind -> contentBefore[index + 1] = contentBefore[index] + if (kind.isLine) 1 else 0 }
    val total = contentBefore[kinds.size]
    val starts = mutableListOf(0)
    for (index in 1 until kinds.size) {
        val isCuttable = contentBefore[index] > 0 &&
            contentBefore[index] < total &&
            kinds[index - 1] != SectionItemKind.COMMENT &&
            kinds[index] != SectionItemKind.BLANK
        if (isCuttable) starts += index
    }
    return starts.toIntArray()
}

/** Whether the item is one of the lines a cut keeps on either side of it. */
private val SectionItemKind.isLine get() = this == SectionItemKind.CONTENT || this == SectionItemKind.RUN
