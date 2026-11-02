/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.language

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong

/**
 * The one direction a song's body is read in, every line of it, so that a line opening with a Latin word or holding only
 * chords is laid out with the lines around it rather than on its own.
 *
 * The languages the song declares decide where every one of them is written from the right ([RightToLeftLanguages]):
 * a Hebrew song says so with `{meta: language he}`, whatever its lines start with. A song that declares none, or
 * declares a language written from the left beside it (a Hebrew song with its English translation), is read the way
 * most of its lyrics' strong letters are: the text it holds is then the better witness than the order of its
 * directives. A tie, a song of no letters (chords only) among them, is left to right.
 *
 * A walk over every lyric, so worked out once per song by whoever keeps the parsed song, never on every frame.
 */
public val ChordProSong.textDirection: WritingDirection
    get() {
        val languages = metadata.languages
        if (languages.isNotEmpty() && languages.all(RightToLeftLanguages::contains)) return WritingDirection.RIGHT_TO_LEFT
        var balance = 0
        blocks.forEach { block ->
            if (block is ChordProBlock.Section) block.lines.forEach { line ->
                if (line is ChordProLine.Lyrics) balance += strongLetterBalanceOf(line.text)
            }
        }
        return if (balance > 0) WritingDirection.RIGHT_TO_LEFT else WritingDirection.LEFT_TO_RIGHT
    }

/**
 * The direction most of [text]'s strong letters are read in, a tie (no letters among them) left to right: how a line or
 * a page of text whose direction nobody declared is read, a PDF's or a plain-text chord sheet's.
 */
public fun writingDirectionOf(text: CharSequence): WritingDirection =
    if (strongLetterBalanceOf(text) > 0) WritingDirection.RIGHT_TO_LEFT else WritingDirection.LEFT_TO_RIGHT

/**
 * How many more strong right-to-left characters [text] holds than letters read from the left. A letter outside the
 * right-to-left blocks is a left-to-right one (Latin, Greek, Cyrillic, CJK); marks, digits, and punctuation are neither.
 */
private fun strongLetterBalanceOf(text: CharSequence): Int {
    var balance = 0
    var index = 0
    while (index < text.length) {
        val char = text[index]
        val next = text.getOrNull(index + 1)
        if (char.isHighSurrogate() && next != null && next.isLowSurrogate()) {
            // A letter of the supplementary planes is counted only where it is written from the right, since Kotlin's
            // common library cannot tell whether a code point past the first plane is a letter.
            if (isStrongRtl(0x10000 + ((char.code - 0xD800) shl 10) + (next.code - 0xDC00))) balance++
            index += 2
        } else {
            when {
                isStrongRtl(char.code) -> balance++
                char.isLetter() -> balance--
            }
            index++
        }
    }
    return balance
}
