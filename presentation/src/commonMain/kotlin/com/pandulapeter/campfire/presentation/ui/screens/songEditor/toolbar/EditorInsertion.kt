/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar

import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting
import org.jetbrains.compose.resources.DrawableResource

/**
 * One thing a toolbar button writes around the selection, or around the caret where there is none.
 *
 * @param isOwnLine True for everything in braces, since ChordPro only reads a directive as one when it is alone on
 *   its line; the two bracketed insertions belong inside a line of lyrics instead.
 * @param metadataName The kind of metadata this describes the song with, for the insertions that go into the header
 *   instead of to the caret, under the name `:chordpro` knows it by. Null for everything that is part of the song.
 * @param emptyPrefix What is written in front of the caret in place of [prefix] where nothing is selected.
 * @param emptySuffix What is written after the caret in place of [suffix] where nothing is selected.
 * @param choices What a button that opens a menu offers, each written as an insertion of its own once it is picked;
 *   empty for a button that writes itself.
 * @param formatting The markup a Bold, Italic, or Underline button toggles around the selection (see
 *   `toggleFormatting`), in place of the two halves; null for every other button.
 * @param icon What the button shows in place of its [label], which then only names it for screen readers: the
 *   formatting buttons', whose icons every text editor shares and which would otherwise take a word each.
 * @param chordShapeInstrument The instrument a Chord shape menu entry writes a `{define}` for (see `insertChordShape`),
 *   in place of the two halves; null for every other entry.
 */
internal data class EditorInsertion(
    val label: String,
    val prefix: String,
    val suffix: String,
    val isOwnLine: Boolean,
    val metadataName: String? = null,
    val emptyPrefix: String = prefix,
    val emptySuffix: String = suffix,
    val choices: List<EditorInsertion> = emptyList(),
    val formatting: Formatting.Style? = null,
    val icon: DrawableResource? = null,
    val chordShapeInstrument: ChordInstrument? = null,
) {

    companion object {

        /** Markup around the selection, inside a line of lyrics or a comment, taken off again where it is there. */
        fun formatting(label: String, style: Formatting.Style, icon: DrawableResource) = EditorInsertion(
            label = label,
            prefix = "",
            suffix = "",
            isOwnLine = false,
            formatting = style,
            icon = icon,
        )

        /** A `{define}` for the chord at the caret, on [instrument], written into the header. */
        fun chordShape(label: String, instrument: ChordInstrument) = EditorInsertion(
            label = label,
            prefix = "",
            suffix = "",
            isOwnLine = true,
            chordShapeInstrument = instrument,
        )

        /** A button that writes nothing itself and asks which of [choices] to write instead. */
        fun menu(label: String, choices: List<EditorInsertion>) = EditorInsertion(
            label = label,
            prefix = "",
            suffix = "",
            isOwnLine = true,
            choices = choices,
        )

        /**
         * A `{start_of_tab}` … `{end_of_tab}` environment around what is selected, or around an empty [staff] with the
         * caret after the name of its top string where nothing is.
         *
         * @param selector The instrument the tab is for, written after its opening directive's name
         *   (`{start_of_tab-guitar}`), or null for one for every instrument. The closing directive carries none, as
         *   ChordPro writes it.
         */
        fun tab(label: String, selector: String?, staff: List<String>): EditorInsertion {
            val section = section(label, name = TAB)
            val prefix = if (selector == null) section.prefix else "{start_of_$TAB-$selector}\n"
            val firstLine = staff.firstOrNull().orEmpty()
            val caret = firstLine.indexOf('|') + 1
            return section.copy(
                prefix = prefix,
                emptyPrefix = prefix + firstLine.take(caret),
                emptySuffix = (listOf(firstLine.drop(caret)) + staff.drop(1)).joinToString("\n") + section.suffix,
            )
        }

        /** A `{start_of_x}` … `{end_of_x}` environment around what is selected, which becomes its first lines. */
        fun section(label: String, name: String) = EditorInsertion(
            label = label,
            prefix = "{start_of_$name}\n",
            suffix = "\n{end_of_$name}",
            isOwnLine = true,
        )

        /**
         * @param hasValue False for the directives that are a word on their own (`{chorus}`), which leave nothing
         *   to type after them and so have no closing half to put the caret in front of.
         */
        fun directive(label: String, name: String, hasValue: Boolean = true) = EditorInsertion(
            label = label,
            prefix = if (hasValue) "{$name: " else "{$name}",
            suffix = if (hasValue) "}" else "",
            isOwnLine = true,
        )

        /** A directive that says what the song *is*, and so belongs in its header rather than at the caret. */
        fun metadata(label: String, name: String) = EditorInsertion(
            label = label,
            prefix = "{$name: ",
            suffix = "}",
            isOwnLine = true,
            metadataName = name,
        )

        /**
         * A custom metadata item, which is how ChordPro carries what it has no directive of its own for — the
         * language of a song, its cover and its links being the three Campfire reads, see `ChordProMetaItems.language`,
         * `ChordProMetaItems.cover` and `ChordProMetaItems.link`. Part of the header like every other [metadata] item, whatever it is spelled as.
         */
        fun meta(label: String, key: String) = EditorInsertion(
            label = label,
            prefix = "{meta: $key ",
            suffix = "}",
            isOwnLine = true,
            metadataName = key,
        )
    }
}

private const val TAB = "tab"
