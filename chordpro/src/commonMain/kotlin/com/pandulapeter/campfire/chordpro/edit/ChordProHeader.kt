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

import com.pandulapeter.campfire.chordpro.voicings.ChordProDefinitions
import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProHeaderLayout
import com.pandulapeter.campfire.chordpro.syntax.ChordProLines
import com.pandulapeter.campfire.chordpro.syntax.ChordProMetaItems
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.META
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
     * words for, and has as many pages about it as somebody linked. Every other metadata directive says one thing about
     * the song, and a file that says it twice is a file with a contradiction in it rather than a richer one, see [write].
     */
    public val repeatableMetadata: Set<String> = MetadataKind.entries.filter { it.isRepeatable }.map { it.longName }.toSet()

    /**
     * The metadata directives a song may say again further down, each later one a change from where it stands rather
     * than a second value (see `ChordProBlock.Timing`): the header's line is the song's own value, and every line of the
     * body is a change. An editor keeps offering them, through [insertChangeable].
     */
    public val changeableMetadata: Set<String> = MetadataKind.entries.filter { it.isTimingChange }.map { it.longName }.toSet()

    /**
     * What an editor's button for a directive of kind [name] writes into [text], and where the caret goes, with [value]
     * as what the directive is to say (the text the user had selected, or nothing):
     * - the [repeatableMetadata] get one more line by [insert], and the [changeableMetadata] whatever [insertChangeable]
     *   decides, since a song may say either again;
     * - every other kind the song does not declare yet gets its line by [insert];
     * - every other kind it does declare, in any spelling and wherever in the file, has its first line taken to instead,
     *   since a second one would be a contradiction rather than a richer song: its value is selected, so that the next
     *   keystroke rewrites it, or replaced by [value] where there is one. A line with no value yet becomes [prefix] and
     *   [suffix], as it would have been written.
     *
     * [value] is read as one line without braces, which would otherwise end the directive and leave the rest in the song
     * as lyrics; one that is blank is no value. Whatever it becomes is selected once written.
     */
    public fun write(text: String, name: String, caretOffset: Int, prefix: String, suffix: String, value: String): Insertion {
        val cleanedValue = value.replace(valueBreakRegex, " ").trim()
        if (name in changeableMetadata) return insertChangeable(text, name, caretOffset, prefix, suffix, cleanedValue)
        if (name in repeatableMetadata) return insert(text, name, prefix, suffix, cleanedValue)
        val lines = ChordProLines.splitLines(text)
        val index = lines.indexOfFirst { line -> ChordProDirectives.matchDirective(line.trim())?.let(ChordProHeaderLayout::metadataKind) == name }
        if (index < 0) return insert(text, name, prefix, suffix, cleanedValue)
        return valueOfLine(lines[index], ChordProLines.lineStartOffsets(text)[index], prefix, suffix, cleanedValue)
    }

    /**
     * The value of [line], a directive starting at [lineStart], selected, or replaced by [value] where there is one. A
     * line with no value yet becomes [prefix] and [suffix] around [value], spelled as the editor writes the directive.
     */
    private fun valueOfLine(line: String, lineStart: Int, prefix: String, suffix: String, value: String): Insertion {
        val directive = ChordProDirectives.matchDirective(line.trim())!!
        val standard = ChordProMetaItems.standardMeta(directive)
        val current = when {
            standard != null -> standard.value
            directive.name == META -> ChordProMetaItems.metaValue(directive)
            else -> directive.value
        }.orEmpty()
        if (current.isEmpty()) {
            val caretOffset = lineStart + prefix.length
            return Insertion(
                offset = lineStart,
                text = prefix + value + suffix,
                caretOffset = caretOffset,
                replacedLength = line.length,
                selectionEnd = caretOffset + value.length,
            )
        }
        val valueStart = lineStart + line.lastIndexOf(current, line.lastIndexOf('}'))
        // Only a value to write is an edit; selecting what is there leaves the text, and so the undo history, alone.
        return Insertion(
            offset = valueStart,
            text = value,
            caretOffset = valueStart,
            replacedLength = if (value.isEmpty()) 0 else current.length,
            selectionEnd = valueStart + value.ifEmpty { current }.length,
        )
    }

    /**
     * Where a directive of kind [name] goes in [text], and what has to be written there: [prefix] and [suffix] are
     * the two halves of the directive as the caller spells it (`{title: ` and `}`), and [value] what is written between
     * them, which is selected once written (the caret alone between the two halves where it is empty).
     *
     * The line goes after the last directive of its own kind, or, for a kind the song does not declare yet, into
     * the header in the order [ChordProHeaderLayout.metadataInsertionIndex] describes. Applying the result is a single
     * insertion at [Insertion.offset] and a caret at [Insertion.caretOffset]; the two are only valid for the text
     * they were computed from.
     */
    public fun insert(text: String, name: String, prefix: String, suffix: String, value: String = ""): Insertion {
        val lines = ChordProLines.splitLines(text)
        val index = ChordProHeaderLayout.metadataInsertionIndex(lines, name)
        val separator = ChordProLines.lineSeparatorOf(text)
        // Past the last line there is nothing to put the directive in front of, so it takes a line break with it
        // instead of leaving one behind, and a file that ended without one still does. A file that ends with a line
        // break has the start of one more line there, which is an ordinary place to insert at.
        val isAppended = index >= lines.size && !ChordProLines.endsWithLineBreak(text)
        val offset = if (isAppended) text.length else ChordProLines.lineStartOffsets(text).getOrElse(index) { text.length }
        val opening = if (isAppended) "$separator$prefix" else prefix
        val caretOffset = offset + opening.length
        return Insertion(
            offset = offset,
            text = if (isAppended) "$opening$value$suffix" else "$opening$value$suffix$separator",
            caretOffset = caretOffset,
            selectionEnd = caretOffset + value.length,
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
     * changes it, so where it goes depends on what the header says and on [caretOffset]; [value] is written as [insert]
     * writes it:
     * - a header with no line of [name] gets one by [insert], since the first value a song is given is its own;
     * - an empty header line of it (one the Song defaults sheet cleared) is the line to type into, and becomes
     *   [prefix] and [suffix] around [value];
     * - a header that names a value and a caret below the body's first line of the song (a line of lyrics, tablature
     *   or a grid, or a `{chorus}` recall) get a line of their own at the start of the caret's line, which never splits
     *   a line of lyrics and puts the change before what it changes;
     * - a header that names a value and a caret in the header or anywhere up to and including that first line, or a
     *   song with no such line yet, get the header line's value selected, or replaced by [value] where there is one,
     *   since a change before anything is played is the song's own value.
     */
    public fun insertChangeable(text: String, name: String, caretOffset: Int, prefix: String, suffix: String, value: String = ""): Insertion {
        val lines = ChordProLines.splitLines(text)
        val bodyStart = ChordProHeaderLayout.bodyStartIndex(lines)
        val headerIndex = (0 until bodyStart).firstOrNull { index ->
            ChordProDirectives.matchDirective(lines[index].trim())?.let(ChordProHeaderLayout::metadataKind) == name
        } ?: return insert(text, name, prefix, suffix, value)
        val lineStarts = ChordProLines.lineStartOffsets(text)
        val headerLine = lines[headerIndex]
        val directive = ChordProDirectives.matchDirective(headerLine.trim())!!
        if ((ChordProMetaItems.standardMeta(directive) ?: directive).value.isNullOrEmpty()) {
            return valueOfLine(headerLine, lineStarts[headerIndex], prefix, suffix, value)
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
        if (caretLine <= firstContentLine) return valueOfLine(headerLine, lineStarts[headerIndex], prefix, suffix, value)
        // A caret past the final line break is on a line of its own already, the one after the last.
        val offset = if (caretOffset >= text.length && ChordProLines.endsWithLineBreak(text)) text.length else lineStarts[caretLine]
        return Insertion(
            offset = offset,
            text = "$prefix$value$suffix${ChordProLines.lineSeparatorOf(text)}",
            caretOffset = offset + prefix.length,
            selectionEnd = offset + prefix.length + value.length,
        )
    }

    private val valueBreakRegex = Regex("[\\r\\n{}]+")

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
