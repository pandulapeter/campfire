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

import com.pandulapeter.campfire.chordpro.chords.ChordProDefinitions
import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProHeaderLayout
import com.pandulapeter.campfire.chordpro.syntax.ChordProLines
import com.pandulapeter.campfire.chordpro.syntax.ChordProMetaItems
import com.pandulapeter.campfire.chordpro.syntax.MetadataKind

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
public object ChordProHeader {

    /**
     * The metadata directives a song may declare more than once, and so the ones an editor keeps offering after the
     * file already carries one: a song has as many tags as it was filed under, is sung in as many languages as it has
     * words for, and has as many pages about it as somebody linked. Every other directive in [declaredMetadata] says one thing about the song, and a file that
     * says it twice is a file with a contradiction in it rather than a richer one.
     */
    public val repeatableMetadata: Set<String> = MetadataKind.entries.filter { it.isRepeatable }.map { it.longName }.toSet()

    /**
     * The metadata directives a song may say again further down, each later one a change from where it stands rather
     * than a second value (see `ChordProBlock.Timing`): the header's line is the song's own value, and every line of the
     * body is a change. An editor keeps offering them, through [insertChangeable].
     */
    public val changeableMetadata: Set<String> = MetadataKind.entries.filter { it.isTimingChange }.map { it.longName }.toSet()

    /**
     * The metadata directives [text] already declares, each under the one name the app knows it by (`{t}` and
     * `{title}` are both `title`, `{meta: language en}` is `language`), and wherever in the file they stand.
     *
     * It is the directives that are counted and not what they are worth, so a `{title: }` waiting to be typed into
     * counts as a title: what this answers is whether writing another one would be writing a second of the same.
     */
    internal fun declaredMetadata(text: String): Set<String> = ChordProLines.splitLines(text)
        .mapNotNullTo(mutableSetOf()) { line -> ChordProDirectives.matchDirective(line.trim())?.let(ChordProHeaderLayout::metadataKind) }

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
    public class DeclaredMetadataCache {
        private var previousText: String? = null
        private var previous: Set<String> = emptySet()

        /** The metadata directives [text] declares, equal to what [declaredMetadata] returns for it. */
        public fun declaredMetadataOf(text: String): Set<String> {
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
            return ChordProDirectives.matchDirective(text.substring(start, end).trim())?.let(ChordProHeaderLayout::metadataKind)
        }
    }

    /**
     * Where a directive of kind [name] goes in [text], and what has to be written there: [prefix] and [suffix] are
     * the two halves of the directive as the caller spells it (`{title: ` and `}`), which is also what decides
     * where between them the caret ends up.
     *
     * The line goes after the last directive of its own kind, or, for a kind the song does not declare yet, into
     * the header in the order [ChordProHeaderLayout.metadataInsertionIndex] describes. Applying the result is a single
     * insertion at [Insertion.offset] and a caret at [Insertion.caretOffset]; the two are only valid for the text
     * they were computed from.
     */
    public fun insert(text: String, name: String, prefix: String, suffix: String): Insertion {
        val lines = ChordProLines.splitLines(text)
        val index = ChordProHeaderLayout.metadataInsertionIndex(lines, name)
        val separator = ChordProLines.lineSeparatorOf(text)
        // Past the last line there is nothing to put the directive in front of, so it takes a line break with it
        // instead of leaving one behind, and a file that ended without one still does. A file that ends with a line
        // break has the start of one more line there, which is an ordinary place to insert at.
        val isAppended = index >= lines.size && !ChordProLines.endsWithLineBreak(text)
        val offset = if (isAppended) text.length else ChordProLines.lineStartOffsets(text).getOrElse(index) { text.length }
        val opening = if (isAppended) "$separator$prefix" else prefix
        return Insertion(
            offset = offset,
            text = if (isAppended) "$opening$suffix" else "$opening$suffix$separator",
            caretOffset = offset + opening.length,
        )
    }

    /**
     * Where a chord definition, [line], goes in [text]: after the last definition of the header, or where there is
     * none, at the end of the header, after everything the song says about itself. Definitions are not among the kinds
     * [ChordProHeaderLayout.metadataOrder] arranges, since Prettify keeps them where they stand and a file that holds some
     * has to come out of it as it always did; this only decides where a new one is added. The caret goes to the start
     * of the chord's name, where one with no name yet is typed.
     */
    public fun insertDefinition(text: String, line: String): Insertion {
        val nameStart = line.indexOf(':') + 2
        if (text.isEmpty()) return Insertion(offset = 0, text = line, caretOffset = nameStart)
        val lines = ChordProLines.splitLines(text)
        val headerEnd = ChordProHeaderLayout.headerEndIndex(lines)
        val lastDefinition = (0 until headerEnd).lastOrNull { index ->
            ChordProDirectives.matchDirective(lines[index].trim())?.let { ChordProDefinitions.selectorOf(it.name) } != null
        }
        val index = lastDefinition?.plus(1) ?: headerEnd
        val separator = ChordProLines.lineSeparatorOf(text)
        val isAppended = index >= lines.size && !ChordProLines.endsWithLineBreak(text)
        val offset = if (isAppended) text.length else ChordProLines.lineStartOffsets(text).getOrElse(index) { text.length }
        val opening = if (isAppended) separator else ""
        return Insertion(
            offset = offset,
            text = if (isAppended) "$opening$line" else "$line$separator",
            caretOffset = offset + opening.length + nameStart,
        )
    }

    /**
     * [insert] for one of the [changeableMetadata], which the header holds once and the body as often as the song
     * changes it, so where it goes depends on what the header says and on [caretOffset]:
     * - a header with no line of [name] gets one by [insert], since the first value a song is given is its own;
     * - an empty header line of it (one the Song defaults sheet cleared) is the line to type into, and becomes
     *   [prefix] and [suffix] with the caret between them;
     * - a header that names a value and a caret below the body's first line of the song (a line of lyrics, tablature
     *   or a grid, or a `{chorus}` recall) get a line of their own at the start of the caret's line, which never splits
     *   a line of lyrics and puts the change before what it changes;
     * - a header that names a value and a caret in the header or anywhere up to and including that first line, or a
     *   song with no such line yet, get nothing written and the header line's value selected, since a change before
     *   anything is played is the song's own value.
     */
    public fun insertChangeable(text: String, name: String, caretOffset: Int, prefix: String, suffix: String): Insertion {
        val lines = ChordProLines.splitLines(text)
        val bodyStart = ChordProHeaderLayout.bodyStartIndex(lines)
        val headerIndex = (0 until bodyStart).firstOrNull { index ->
            ChordProDirectives.matchDirective(lines[index].trim())?.let(ChordProHeaderLayout::metadataKind) == name
        } ?: return insert(text, name, prefix, suffix)
        val lineStarts = ChordProLines.lineStartOffsets(text)
        val headerLine = lines[headerIndex]
        val headerLineStart = lineStarts[headerIndex]
        val directive = ChordProDirectives.matchDirective(headerLine.trim())!!
        val value = (ChordProMetaItems.standardMeta(directive) ?: directive).value.orEmpty()
        if (value.isEmpty()) {
            return Insertion(
                offset = headerLineStart,
                text = prefix + suffix,
                caretOffset = headerLineStart + prefix.length,
                replacedLength = headerLine.length,
            )
        }
        val caretLine = lineStarts.indexOfLast { it <= caretOffset }
        // Nothing of the song is played before its first line, so a change there would be the song's own value written
        // in the wrong place. A recall is a part of the song, as a line of lyrics is.
        val firstContentLine = (bodyStart until lines.size).firstOrNull { index ->
            val trimmed = lines[index].trim()
            if (trimmed.isEmpty() || trimmed.startsWith('#')) return@firstOrNull false
            val directive = ChordProDirectives.matchDirective(trimmed) ?: return@firstOrNull true
            directive.name == "chorus"
        } ?: lines.size
        if (caretLine <= firstContentLine) {
            val valueStart = headerLineStart + headerLine.lastIndexOf(value, headerLine.lastIndexOf('}'))
            return Insertion(offset = valueStart, text = "", caretOffset = valueStart, selectionEnd = valueStart + value.length)
        }
        // A caret past the final line break is on a line of its own already, the one after the last.
        val offset = if (caretOffset >= text.length && ChordProLines.endsWithLineBreak(text)) text.length else lineStarts[caretLine]
        return Insertion(offset = offset, text = "$prefix$suffix${ChordProLines.lineSeparatorOf(text)}", caretOffset = offset + prefix.length)
    }

    /**
     * @param offset The character offset the [text] is inserted at.
     * @param caretOffset Where the caret belongs once it has been, which is between the two halves of the directive,
     *   or the start of what is selected.
     * @param replacedLength How much of the text at [offset] the [text] takes the place of.
     * @param selectionEnd The end of what is selected once it has been, which is [caretOffset] for no selection.
     */
    public data class Insertion(
        val offset: Int,
        val text: String,
        val caretOffset: Int,
        val replacedLength: Int = 0,
        val selectionEnd: Int = caretOffset,
    )
}
