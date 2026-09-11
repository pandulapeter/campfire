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
     */
    fun addTag(text: String, tag: String): String {
        val trimmedTag = tag.trim()
        if (trimmedTag.isEmpty() || ChordProParser.parseMetadata(text).tags.any { it.equals(trimmedTag, ignoreCase = true) }) return text
        val lines = ChordProSyntax.splitLines(text).toMutableList()
        lines.add(ChordProSyntax.metadataInsertionIndex(lines, ChordProSyntax.TAG_NAME), "{${ChordProSyntax.TAG_NAME}: $trimmedTag}")
        return lines.joinToString("\n")
    }

    /**
     * Drops every tag directive naming [tag], whichever of the two spellings of it the file uses. A directive holds
     * one tag, so there is never anything left of the line to keep.
     */
    fun removeTag(text: String, tag: String): String {
        val trimmedTag = tag.trim()
        if (trimmedTag.isEmpty()) return text
        return ChordProSyntax.splitLines(text)
            .filterNot { line -> line.tag()?.equals(trimmedTag, ignoreCase = true) == true }
            .joinToString("\n")
    }

    /** The tag of a line that is a tag directive, null for every other line. */
    private fun String.tag() = ChordProSyntax.matchDirective(trim())?.let { ChordProSyntax.tag(it) }
}
