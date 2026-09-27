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

/**
 * The block of directives a song opens with, for an editor that writes into it while the caret is somewhere else
 * entirely.
 *
 * ChordPro reads a `{title}` as the title of the song wherever it stands, so a directive written at the caret is
 * valid in the middle of a verse and lands nowhere near the rest of what the song says about itself. This is where
 * such a directive belongs instead, and where the caret then goes, since one inserted from a toolbar is one the
 * user is about to type the value of.
 *
 * Nothing already written is moved or rewritten: what comes back is one line to insert at one offset, so that the
 * user's own formatting — their line endings, their blank lines, the order they chose to list a header in —
 * survives a button being tapped, for the same reason [ChordProTags] edits the text rather than the model.
 */
object ChordProHeader {

    /**
     * The metadata directives a song may declare more than once, and so the ones an editor keeps offering after the
     * file already carries one: a song has as many tags as it was filed under, and is sung in as many languages as
     * it has words for. Every other directive in [declaredMetadata] says one thing about the song, and a file that
     * says it twice is a file with a contradiction in it rather than a richer one.
     */
    val repeatableMetadata = setOf(ChordProSyntax.TAG_NAME, ChordProSyntax.LANGUAGE_NAME)

    /**
     * The metadata directives [text] already declares, each under the one name the app knows it by (`{t}` and
     * `{title}` are both `title`, `{meta: language en}` is `language`), and wherever in the file they stand.
     *
     * It is the directives that are counted and not what they are worth, so a `{title: }` waiting to be typed into
     * counts as a title: what this answers is whether writing another one would be writing a second of the same.
     */
    fun declaredMetadata(text: String): Set<String> = ChordProSyntax.splitLines(text)
        .mapNotNullTo(mutableSetOf()) { line -> ChordProSyntax.matchDirective(line.trim())?.let(ChordProSyntax::metadataKind) }

    /**
     * [declaredMetadata] for a text that is edited one keystroke at a time, which is how the editor's toolbar keeps a
     * directive typed by hand from being offered again; the answer is always equal to [declaredMetadata]'s.
     *
     * An edit that adds or removes no line break, and does not split or join a CRLF pair, changes the text of exactly
     * one line, and the set can only change if that line's kind does: a keystroke in a lyric line or in a directive's
     * value leaves it as it was, and returns the set it had. Anything else is counted again over the whole text, the
     * keystroke that completes or breaks a directive included.
     *
     * One instance follows one text; it is not safe to share between threads.
     */
    class DeclaredMetadataCache {
        private var previousText: String? = null
        private var previous: Set<String> = emptySet()

        /** The metadata directives [text] declares, equal to what [declaredMetadata] returns for it. */
        fun declaredMetadataOf(text: String): Set<String> {
            val oldText = previousText
            if (oldText != null) {
                if (text === oldText || text == oldText) return previous
                val change = ChordProTextChange.between(oldText, text)
                if (isWithinOneLine(oldText, text, change) && kindOfLineAt(oldText, change.oldStart) == kindOfLineAt(text, change.oldStart)) {
                    previousText = text
                    return previous
                }
            }
            previous = declaredMetadata(text)
            previousText = text
            return previous
        }

        /** Neither side of [change] holds a line break, and it does not sit between the two halves of a CRLF. */
        private fun isWithinOneLine(old: String, new: String, change: ChordProTextChange): Boolean {
            for (offset in change.oldStart until change.oldEnd) if (old[offset] == '\r' || old[offset] == '\n') return false
            for (offset in change.oldStart until change.newEnd) if (new[offset] == '\r' || new[offset] == '\n') return false
            return !(change.oldStart > 0 && old[change.oldStart - 1] == '\r' && old.getOrNull(change.oldEnd) == '\n')
        }

        /** The metadata kind of the line around [offset], by the same rule [declaredMetadata] applies to every line. */
        private fun kindOfLineAt(text: String, offset: Int): String? {
            var start = offset
            while (start > 0 && text[start - 1] != '\n' && text[start - 1] != '\r') start--
            var end = offset
            while (end < text.length && text[end] != '\n' && text[end] != '\r') end++
            return ChordProSyntax.matchDirective(text.substring(start, end).trim())?.let(ChordProSyntax::metadataKind)
        }
    }

    /**
     * Where a directive of kind [name] goes in [text], and what has to be written there: [prefix] and [suffix] are
     * the two halves of the directive as the caller spells it (`{title: ` and `}`), which is also what decides
     * where between them the caret ends up.
     *
     * The line goes after the last directive of its own kind, or, for a kind the song does not declare yet, into
     * the header in the order [ChordProSyntax.metadataInsertionIndex] describes. Applying the result is a single
     * insertion at [Insertion.offset] and a caret at [Insertion.caretOffset]; the two are only valid for the text
     * they were computed from.
     */
    fun insert(text: String, name: String, prefix: String, suffix: String): Insertion {
        val lines = ChordProSyntax.splitLines(text)
        val index = ChordProSyntax.metadataInsertionIndex(lines, name)
        val separator = ChordProSyntax.lineSeparatorOf(text)
        // Past the last line there is nothing to put the directive in front of, so it takes a line break with it
        // instead of leaving one behind, and a file that ended without one still does. A file that ends with a line
        // break has the start of one more line there, which is an ordinary place to insert at.
        val isAppended = index >= lines.size && !ChordProSyntax.endsWithLineBreak(text)
        val offset = if (isAppended) text.length else ChordProSyntax.lineStartOffsets(text).getOrElse(index) { text.length }
        val opening = if (isAppended) "$separator$prefix" else prefix
        return Insertion(
            offset = offset,
            text = if (isAppended) "$opening$suffix" else "$opening$suffix$separator",
            caretOffset = offset + opening.length,
        )
    }

    /**
     * @param offset The character offset the [text] is inserted at, which is always the start of a line.
     * @param caretOffset Where the caret belongs once it has been, which is between the two halves of the directive.
     */
    data class Insertion(
        val offset: Int,
        val text: String,
        val caretOffset: Int,
    )
}
