/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.model

/**
 * One top-level element of a song body.
 */
sealed interface ChordProBlock {

    /**
     * An environment or an implicit paragraph. Its [lines] may switch between lyrics, tablature and grids as the
     * song does, since those are ways of writing a line down rather than sections of their own.
     */
    data class Section(
        val type: SectionType,
        val label: String?, // "Verse 1" from {start_of_verse: Verse 1} or {sov: label="Verse 1"}
        val lines: List<ChordProLine>,
        /**
         * Whether this is the rest of the section before it, which a comment, a break or a chorus recall standing
         * inside that section cut in two. It is still the section the file wrote once, so it has no heading of its own
         * to show, and a recall of a chorus repeats it together with the part before it.
         */
        val isContinuation: Boolean = false,
        /**
         * Which of the unlabeled sections of its kind this is, for a viewer that heads those with their kind and a
         * number. The parser never sets it, since the file does not say it: it is a way of showing the section, and the
         * label stays what the file wrote.
         */
        val number: Int? = null,
    ) : ChordProBlock

    /**
     * {chorus} / {chorus: label}: repeat the most recent chorus. [blocks] is that chorus as the parser found it — its
     * first section, and where something cut it, whatever stood inside it and the continuations after — or empty where
     * no chorus came before. The renderer decides how to show it.
     */
    data class ChorusRecall(
        val label: String?,
        val blocks: List<ChordProBlock> = emptyList(),
    ) : ChordProBlock

    /**
     * `{comment}` and its styled forms. A comment written inside a section is a block of its own like any other, since
     * the section's lines are cut around it, so where it stood is recorded with it: a viewer folds it away with its
     * section and leaves the ones between sections alone.
     */
    data class Comment(
        val text: String,
        val style: CommentStyle,
        val placement: CommentPlacement = CommentPlacement.BETWEEN_SECTIONS,
        /**
         * Whether it was written inside a `{start_of_tab}` or a `{start_of_grid}` that has a line of its own, which
         * makes it a note about the tablature or the grid: it says nothing where they are not shown.
         */
        val isInTabOrGrid: Boolean = false,
    ) : ChordProBlock

    /**
     * `{transpose: N}` somewhere after the song has begun: from here on the chords are read [semitones] away from
     * where the song's own transposition ([ChordProMetadata.transpose], the `{transpose}` it opens with) puts them —
     * a key change written as a directive. 0 is back to that. It shows nothing; the transposition applies it.
     */
    data class Transpose(val semitones: Int) : ChordProBlock

    /** {column_break} / {new_page} and friends: a hint that the layout may break here. */
    data object Break : ChordProBlock
}

/**
 * What a section of a song *is*. Deliberately not how its lines are written down: `{start_of_tab}` and
 * `{start_of_grid}` say that the lines inside them are tablature or a chord grid, which is a display mode rather
 * than a part of the song, and they are carried by [ChordProLine.Tab] and [ChordProLine.Grid] instead. A solo can
 * then be one section holding a line of chords and the tablature under it, rather than three sections in a row.
 */
sealed interface SectionType {

    data object Verse : SectionType

    data object Chorus : SectionType

    data object Bridge : SectionType

    /** {start_of_<name>} for any other name, e.g. "intro", "solo", "outro", "pre-chorus". */
    data class Custom(val name: String) : SectionType

    /** Lines outside any environment, grouped by blank lines. Rendered like a verse without a label. */
    data object Paragraph : SectionType
}

enum class CommentStyle { PLAIN, ITALIC, BOX }

/** Which section a [ChordProBlock.Comment] was written in. */
enum class CommentPlacement {

    /**
     * Outside every environment, or among lines that are in none, where it belongs to no section; also inside a
     * section that ended up with no line, since there is nothing for it to belong to.
     */
    BETWEEN_SECTIONS,

    /** Inside a section before its first line, so it belongs to the [ChordProBlock.Section] that follows it. */
    START_OF_SECTION,

    /**
     * Inside a section after a line of it, so it belongs to the [ChordProBlock.Section] before it — and to the
     * continuation after it, where one follows.
     */
    IN_SECTION,
}
