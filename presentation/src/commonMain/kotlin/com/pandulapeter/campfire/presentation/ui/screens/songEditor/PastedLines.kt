/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.edit.ChordProLiteralLines
import com.pandulapeter.campfire.chordpro.edit.ChordProPrettifier

/** Lines put into the editor's field in one edit, starting at [start] of the text as that edit left it. */
internal data class PastedLines(val start: Int, val text: String)

/**
 * What one change of an edit really put into the field, and where: [inserted] starting at [start], over [replaced],
 * with what the two begin and end with alike taken off. The web's text input reports a typed character as the whole
 * text replaced by the whole text with the character in it, which would read as a paste of every line of the song; a
 * paste or a keystroke elsewhere already arrives as only what it inserted, and is answered unchanged. The ends never
 * stop between the two halves of a surrogate pair.
 */
internal fun narrowedInsertion(start: Int, replaced: CharSequence, inserted: CharSequence): Pair<Int, CharSequence> {
    var prefix = 0
    val shorter = minOf(replaced.length, inserted.length)
    while (prefix < shorter && replaced[prefix] == inserted[prefix]) prefix++
    if (prefix > 0 && inserted[prefix - 1].isHighSurrogate()) prefix--
    var suffix = 0
    while (suffix < shorter - prefix && replaced[replaced.length - 1 - suffix] == inserted[inserted.length - 1 - suffix]) suffix++
    if (suffix > 0 && inserted[inserted.length - suffix].isLowSurrogate()) suffix--
    return start + prefix to inserted.subSequence(prefix, inserted.length - suffix)
}

/**
 * The paste among the [insertions] of one edit (where each inserted text starts, and the text) worth formatting for:
 * the last one that brought two lines or more with something on them. A typed character, a typed line
 * break and a paste of one line are none; a paste over a selection is where the selection started.
 */
internal fun pastedLinesOf(insertions: List<Pair<Int, CharSequence>>): PastedLines? = insertions
    .lastOrNull { (_, text) -> text.lineSequence().count { it.isNotBlank() } >= 2 }
    ?.let { (start, text) -> PastedLines(start = start, text = text.toString()) }

/**
 * What the field holding [text] becomes once [paste] has arrived in it, and where the caret goes, or null where it stays
 * as the paste left it. Lines with chords written over the lyrics are converted first (`ChordSheetConverter.convertFragment`:
 * headings become sections, no title is guessed), with the caret after them, unless the paste's first line lands in a
 * tab, a grid, or another environment whose lines are its own notation (`ChordProLiteralLines`); then the whole song is
 * formatted as the menu's Prettify formats it, the caret kept next to its text, since a paste is where the spacing
 * between sections and around the header goes wrong. Neither happens in a document longer than [LONG_DOCUMENT_LENGTH]: the edit is recorded
 * in the undo history as a whole, and there it would be the whole text twice.
 */
internal fun formattedAfterPaste(text: String, paste: PastedLines): Pair<String, Int>? {
    if (text.length > LONG_DOCUMENT_LENGTH) return null
    val firstLine = paste.start + paste.text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
    val converted = if (ChordProLiteralLines.isLiteralAt(text, firstLine)) null else ChordSheetConverter.convertFragment(paste.text)
    val withPaste = converted?.let { text.replaceRange(paste.start, paste.start + paste.text.length, it) } ?: text
    val caret = paste.start + (converted ?: paste.text).length
    val prettified = ChordProPrettifier.prettify(withPaste)
    return if (prettified == text) null else prettified to ChordProPrettifier.prettifiedOffset(withPaste, prettified, caret)
}
