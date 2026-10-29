/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.chordpro.model.TabRowLine

/**
 * The proportions tablature is engraved in, on screen ([TabStaffLines]) and in the PDF alike, each a fraction of the
 * lyrics' size. They are abc2svg's for its tab staff (its `tabfont`, bold 7 on a staff scaled by 1.6, lines 6 units
 * apart, beside the lyrics' 16 units of `AbcRendererImpl`), so that a song's tabs and its sheet music read as one
 * engraving.
 */
internal object TabEngraving {

    /** The size of a fret number. */
    const val FRET_SIZE_PER_EM = 7 * 1.6f / 16

    /** The distance between two strings, and between two systems, so that their strings are never read as one staff. */
    const val STRING_SPACING_PER_EM = 6 * 1.6f / 16

    /** A column is a fret's figure with a little air either side, which the string is broken by. */
    const val COLUMN_WIDTH_PER_FIGURE = 1.25f

    /**
     * The least gap between two words of a line over or under the staff, in columns: a word space of the lyrics' face.
     * Each word starts at its own column, and the chord names and words are wider per character than a column is, so
     * one written a single space after another would otherwise touch it; only then is it moved right, and by no more
     * than this gap asks, since a whole column would put it over the next note rather than the one it was written over.
     */
    const val MIN_WORD_GAP_PER_COLUMN = 0.5f

    /** The width of a bar line or a string, in columns. */
    const val STROKE_WIDTH_PER_COLUMN = 0.08f

    /** How far a bar line on a string with none above or under it stops short of the lines next to it, in lines. */
    const val LONE_BAR_INSET = 0.15f

    /** Inter's capitals and lining figures, which every platform engraves tabs in, and its ascent above the baseline. */
    const val CAP_HEIGHT_PER_EM = 1490 / 2048f
    const val ASCENT_PER_EM = 1984 / 2048f

    const val TABULAR_FIGURES = "tnum"
}

/**
 * Where the name of a staff line's string ends (`e` of `e|---`): what stands before its first string or bar. A name is
 * set in the regular weight, the frets after it in bold, as a tab staff labels its strings. A piece of a line cut off a
 * system has no name, the wrapper repeating the name on every system.
 */
internal val TabRowLine.stringNameEnd get() = text.indexOfFirst { it == '-' || it == '|' }.let { if (it < 0) 0 else it }
