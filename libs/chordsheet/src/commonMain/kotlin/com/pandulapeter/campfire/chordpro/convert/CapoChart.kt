/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary

/**
 * [song] as the capo chart it was printed from, where its chords are named as they sound over a [capo]: a Campfire
 * file's chords under a `{capo}` are the shapes played above it, so they and the key move down by the capo, while a
 * tab's frets, printed counted from the capo, stay. The move reads only the standard notation (a Latin name is no
 * chord to it), so the text is brought into it first, as the import would bring it afterwards. The song's last
 * [keyChanges] `{key}` lines are the key changes written in its body, and each part from one on is moved apart, spelled
 * by its own key moved down, which the move reads off the part's opening `{key}`: one move of the whole would spell
 * a flat key's chorus by the sharp key the song starts in.
 */
internal fun asCapoChart(song: String, capo: Int, keyChanges: Int): String {
    if (capo == 0) return song
    val lines = ChordProNotation.convertText(song, ChordNotation.STANDARD, ChordNotation.STANDARD).split('\n')
    val cuts = lines.indices.filter { lines[it].startsWith("{${ChordProVocabulary.KEY}: ") }.takeLast(keyChanges).filter { it > 0 }
    return (listOf(0) + cuts + lines.size).zipWithNext { from, until ->
        ChordProTransposer.transposeText(lines.subList(from, until).joinToString("\n"), -capo, capo = -capo)
    }.joinToString("\n")
}
