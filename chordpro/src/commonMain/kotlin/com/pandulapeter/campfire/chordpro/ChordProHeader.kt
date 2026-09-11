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
        val lines = text.split('\n')
        val index = ChordProSyntax.metadataInsertionIndex(lines, name)
        // Past the last line there is nothing to put the directive in front of, so it takes a line break with it
        // instead of leaving one behind, and a file that ended without one still does.
        val isAppended = index >= lines.size
        val offset = if (isAppended) text.length else lines.take(index).sumOf { it.length + 1 }
        val opening = if (isAppended) "\n$prefix" else prefix
        return Insertion(
            offset = offset,
            text = if (isAppended) "$opening$suffix" else "$opening$suffix\n",
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
