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

import com.pandulapeter.campfire.chordpro.tabs.ChordProTabWrapper

/**
 * One line inside a [ChordProBlock.Section].
 */
public sealed interface ChordProLine {

    /**
     * A lyrics line: text with chords anchored at character offsets, and the [formatting] its markup (`<b>`, `<i>`,
     * `<u>`) gave ranges of it, ordered by where they start, then by style. The tags themselves are not part of [text].
     */
    public data class Lyrics(
        val text: String,
        val chords: List<Chord>,
        val formatting: List<Formatting> = emptyList(),
    ) : ChordProLine {

        public data class Chord(
            val position: Int, // offset into text where the chord sits
            val name: String, // "Am7", "N.C.", ...
            val isAnnotation: Boolean, // [*text] annotations: shown like a chord, never transposed
            /**
             * A repeat count or bar line written as text on a chord row (`[G] [C] x4`), see
             * [com.pandulapeter.campfire.chordpro.syntax.ChordProRowMarks]. A mark is always an [isAnnotation] too, so
             * whatever skips annotations skips it; it differs only in being shown with the chords rather than the
             * comments, and in standing in the file as its bare text. The [text] it was written in is blank there.
             */
            val isMark: Boolean = false,
        )

        /**
         * The characters of [text] from [start] (inclusive) to [end] (exclusive) set in [style], never empty. Ranges of
         * different styles may overlap, and one of a style never overlaps or touches another of the same style.
         */
        public data class Formatting(
            val start: Int,
            val end: Int,
            val style: Style,
        ) {

            public enum class Style { BOLD, ITALIC, UNDERLINE }
        }
    }

    /**
     * One line inside {start_of_tab}: monospaced, cut at its columns rather than reflowed by its words when it is
     * too wide (see [ChordProTabWrapper]); transposed on the frets, not on the
     * notes. A run of them can sit anywhere inside a section, with lyrics before and after it.
     */
    public data class Tab(
        val text: String,
        /**
         * Whether the tab line before this one in the section was written in the same `{start_of_tab}` environment.
         * A run of tablature is moved as one fingerboard for as long as this holds; a second environment in the same
         * section, even one only a blank line away, is a fingerboard of its own, as it is to the text transposition.
         */
        val continuesEnvironment: Boolean = false,
        /** The label of the `{start_of_tab}` the line was written in (`{start_of_tab: Riff}`), on every line of it. */
        val label: String? = null,
        /**
         * The selector of the `{start_of_tab}` the line was written in, on every line of it: the instrument it is
         * for (`guitar` for `{start_of_tab-guitar}`), with a `!` after it where it is for every instrument but that
         * one, or null for a tab written for everybody. See [com.pandulapeter.campfire.chordpro.tabs.ChordProTabs.select].
         */
        val selector: String? = null,
        /**
         * What the reader is told about how the run this line starts was moved to their instrument, on the first line
         * of a run only, and only on a song the transposition moved for a [TabPlayer]. See [TabNotice].
         */
        val notice: TabNotice? = null,
    ) : ChordProLine

    /** One line inside {start_of_grid}: tokens separated by whitespace. */
    public data class Grid(
        val tokens: List<GridToken>,
        /** The label of the `{start_of_grid}` the line was written in, on every line of it. */
        val label: String? = null,
    ) : ChordProLine

    /**
     * One line inside an environment ChordPro hands to another program (`{start_of_abc}`, `{start_of_ly}`,
     * `{start_of_svg}`), kept exactly as written: that program's notation, which has no chords, markup, or definitions
     * to this module however much of it looks like them (ABC's `"G"` and `[CEG]`). Such an environment is always a
     * section of its own, so a run of these lines is the whole section. `{start_of_textblock}` is prose and gives
     * [Lyrics] instead.
     */
    public data class Verbatim(
        val text: String,
        /** The environment the line was written in, lowercase: `abc`, `ly`, or `svg`. */
        val environment: String,
    ) : ChordProLine

    /** An empty line inside an environment. */
    public data object Blank : ChordProLine
}

public sealed interface GridToken {

    public data class Bar(val text: String) : GridToken // "|", "||", "|.", "|:", ":|", ":|:", voltas such as "|1" and ":|2>"

    public data class Chord(val name: String) : GridToken // "Am", or "C~G" for several chords in one cell

    public data object Beat : GridToken // "."

    public data class Repeat(val text: String) : GridToken // "%" (repeat previous cell), "%%"

    public data class Text(val text: String) : GridToken // a margin label before the first bar, a comment after the last one, or a "/" chord position
}
