/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.edit

import com.pandulapeter.campfire.chordpro.syntax.ChordProLineScanner
import com.pandulapeter.campfire.chordpro.syntax.ChordProLines
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.GRID
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.TAB

/** Where the lines of a ChordPro text are not lyrics but an environment's own notation, see [isLiteralAt]. */
public object ChordProLiteralLines {

    /**
     * Whether the line of [text] holding [offset] stands inside a tab, a grid, or an environment handed to another
     * program (ABC, LilyPond, SVG, a text block), read by the parser's rule. Such lines are that notation as written: a
     * grid's `| C . . . | G . . . |` is a row of cells, not a chord row over a lyric, and nothing there may be rewritten
     * as a chord sheet would be. An [offset] past the end is on the last line.
     */
    public fun isLiteralAt(text: String, offset: Int): Boolean {
        val prefix = text.substring(0, offset.coerceIn(0, text.length))
        val lineIndex = ChordProLines.splitLines("$prefix.").lastIndex
        val line = ChordProLineScanner.scan(text).elementAtOrNull(lineIndex) ?: return false
        return line.isDelegated || line.environment == TAB || line.environment == GRID
    }
}
