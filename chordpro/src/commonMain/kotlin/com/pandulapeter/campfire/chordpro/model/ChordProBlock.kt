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
    ) : ChordProBlock

    /** {chorus} / {chorus: label}: repeat the most recent chorus. The renderer decides how to show it. */
    data class ChorusRecall(val label: String?) : ChordProBlock

    data class Comment(val text: String, val style: CommentStyle) : ChordProBlock

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
