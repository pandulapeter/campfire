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
 * Adds and removes the tags of a song directly in its text, leaving every other byte of it exactly as it was. The
 * same reasoning as [ChordProTransposer.transposeText]: what comes out of here is written back to the user's own
 * file, so the formatting they chose has to survive an edit made from a chip in the app bar of the viewer.
 *
 * Two spellings of the same word are the same tag, here as everywhere else, so a tag is matched without regard to
 * case and adding one the song already carries changes nothing.
 */
object ChordProTags {

    /**
     * Writes a `{tag}` directive for [tag], after the last tag the file already has or, if it has none, at the end of
     * the directives it opens with. A blank tag, or one the song already carries, returns the text unchanged.
     *
     * @param fold What two spellings of a tag are compared as, besides their case — the caller's Unicode normalization,
     *   which this module has none of. The tag is written as it was given.
     */
    fun addTag(text: String, tag: String, fold: (String) -> String = { it }): String {
        val trimmedTag = tag.asTag()
        val key = fold(trimmedTag)
        if (trimmedTag.isEmpty() || ChordProParser.parseMetadata(text).tags.any { fold(it).equals(key, ignoreCase = true) }) return text
        val lines = ChordProSyntax.splitLines(text).toMutableList()
        lines.add(ChordProSyntax.metadataInsertionIndex(lines, ChordProSyntax.TAG_NAME), "{${ChordProSyntax.TAG_NAME}: $trimmedTag}")
        return ChordProSyntax.joinLines(lines, text)
    }

    /**
     * Drops every tag directive naming [tag], whichever of the two spellings of it the file uses. A directive holds
     * one tag, so there is never anything left of the line to keep.
     *
     * @param fold As for [addTag]: every spelling it folds to the same key is dropped, a decomposed one included.
     */
    fun removeTag(text: String, tag: String, fold: (String) -> String = { it }): String {
        val trimmedTag = tag.asTag()
        if (trimmedTag.isEmpty()) return text
        val key = fold(trimmedTag)
        val lines = ChordProSyntax.splitLines(text).filterNot { line -> line.tag()?.let(fold)?.equals(key, ignoreCase = true) == true }
        return ChordProSyntax.joinLines(lines, text)
    }

    /** The tag of a line that is a tag directive, null for every other line. */
    private fun String.tag() = ChordProSyntax.matchDirective(trim())?.let { ChordProSyntax.tag(it) }

    /** A tag is one line of the file, so the line breaks a pasted value may carry are the spaces between its words. */
    private fun String.asTag() = replace(lineBreakRegex, " ").trim()

    private val lineBreakRegex = Regex("[\\r\\n]+")
}
