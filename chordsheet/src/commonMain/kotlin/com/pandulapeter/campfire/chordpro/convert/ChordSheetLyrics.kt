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

import com.pandulapeter.campfire.chordpro.edit.ChordProLiteralText

/** Lyrics without chords, written into a ChordPro song by a reader of another app's format. */
public object ChordSheetLyrics {

    /**
     * [text] with every line escaped the way [ChordSheetConverter] escapes prose, so that a lyric's `[`, a line in
     * braces, or a leading `#` never becomes ChordPro markup: what [ChordSheetConverter.convertFragment] declines,
     * having no chord row to set, still has to be written safely.
     */
    public fun escaped(text: String): String = text.lines().joinToString(separator = "\n", transform = ChordProLiteralText::escape)
}
