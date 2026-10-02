/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

/** ChordPro has no literal escape. Keep extracted prose visible without introducing parser syntax. */
object ChordProLiteralText {
    fun escape(line: String): String {
        var result = line.replace('[', '(').replace(']', ')')
        val trimmed = result.trim()
        if (trimmed.startsWith('{') && trimmed.endsWith('}')) result = result.replace('{', '(').replace('}', ')')
        val first = result.indexOfFirst { !it.isWhitespace() }
        if (first >= 0 && result[first] == '#') result = result.replaceRange(first, first + 1, "\uff03")
        return result
    }
}
