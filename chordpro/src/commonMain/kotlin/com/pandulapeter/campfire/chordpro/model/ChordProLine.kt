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

    /** One line inside {start_of_tab}: monospaced, never reflowed; transposed on the frets, not on the notes. */
    data class Tab(val text: String) : ChordProLine

    /** One line inside {start_of_grid}: tokens separated by whitespace. */
    data class Grid(val tokens: List<GridToken>) : ChordProLine

    /** An empty line inside an environment. */
    data object Blank : ChordProLine
}

sealed interface GridToken {

    data class Bar(val text: String) : GridToken // "|", "||", "|:", ":|", "|."

    data class Chord(val name: String) : GridToken

    data object Beat : GridToken // "."

    data class Repeat(val text: String) : GridToken // "%" (repeat previous cell), "%%"

    data class Text(val text: String) : GridToken // anything else, e.g. a comment after the last bar
}
