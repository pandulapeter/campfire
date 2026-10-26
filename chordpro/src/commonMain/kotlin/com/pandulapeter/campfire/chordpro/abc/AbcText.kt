/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.abc

/** Tells a text written in [ABC notation](https://abcnotation.com/wiki/abc:standard:v2.1) from any other. */
public object AbcText {

    private const val BYTE_ORDER_MARK = '﻿'

    /**
     * Whether [text] is ABC: the lines before its first line of music, blank lines and `%` comments aside, are all
     * information fields, at least one of them `X:` or `T:`, and one of them the `K:` that ends a tune header. Anything
     * else first (a ChordPro directive, a line of prose, a chord row) means it isn't, even with a `K:` further down,
     * which keeps a ChordPro file holding an `{start_of_abc}` block, and prose that happens to have a `K:` line, as
     * what they are. Reads only as far as that first line of music.
     */
    public fun isAbc(text: String): Boolean {
        var hasTuneField = false
        for (line in text.lineSequence()) {
            val trimmed = line.trim().trimStart(BYTE_ORDER_MARK)
            if (trimmed.isEmpty() || AbcLines.isComment(trimmed)) continue
            when (AbcLines.field(trimmed)?.letter ?: return false) {
                'X', 'T' -> hasTuneField = true
                'K' -> return hasTuneField
            }
        }
        return false
    }
}
