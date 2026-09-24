/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.model

/**
 * One line inside a [ChordProBlock.Section].
 */
sealed interface ChordProLine {

    /** A lyrics line: text with chords anchored at character offsets. */
    data class Lyrics(val text: String, val chords: List<Chord>) : ChordProLine {

        data class Chord(
            val position: Int, // offset into text where the chord sits
            val name: String, // "Am7", "N.C.", ...
            val isAnnotation: Boolean, // [*text] annotations: shown like a chord, never transposed
        )
    }

    /**
     * One line inside {start_of_tab}: monospaced, cut at its columns rather than reflowed by its words when it is
     * too wide (see [com.pandulapeter.campfire.chordpro.ChordProTabWrapper]); transposed on the frets, not on the
     * notes. A run of them can sit anywhere inside a section, with lyrics before and after it.
     */
    data class Tab(
        val text: String,
        /**
         * Whether the tab line before this one in the section was written in the same `{start_of_tab}` environment.
         * A run of tablature is moved as one fingerboard for as long as this holds; a second environment in the same
         * section, even one only a blank line away, is a fingerboard of its own, as it is to the text transposition.
         */
        val continuesEnvironment: Boolean = false,
        /** The label of the `{start_of_tab}` the line was written in (`{start_of_tab: Riff}`), on every line of it. */
        val label: String? = null,
    ) : ChordProLine

    /** One line inside {start_of_grid}: tokens separated by whitespace. */
    data class Grid(
        val tokens: List<GridToken>,
        /** The label of the `{start_of_grid}` the line was written in, on every line of it. */
        val label: String? = null,
    ) : ChordProLine

    /** An empty line inside an environment. */
    data object Blank : ChordProLine
}

sealed interface GridToken {

    data class Bar(val text: String) : GridToken // "|", "||", "|.", "|:", ":|", ":|:", voltas such as "|1" and ":|2>"

    data class Chord(val name: String) : GridToken // "Am", or "C~G" for several chords in one cell

    data object Beat : GridToken // "."

    data class Repeat(val text: String) : GridToken // "%" (repeat previous cell), "%%"

    data class Text(val text: String) : GridToken // a margin label before the first bar, a comment after the last one, or a "/" chord position
}
