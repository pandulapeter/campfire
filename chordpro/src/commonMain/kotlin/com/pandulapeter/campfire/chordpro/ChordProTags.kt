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
        lines.add(insertionIndex(lines), "{${ChordProSyntax.TAG_NAME}: $trimmedTag}")
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

    /**
     * Where a new tag directive goes: right after the last one the file has, wherever that is, so that the tags of a
     * song stay together. A file with no tags yet gets it at the end of the block of directives it opens with, which
     * is where its metadata is; one that opens with content rather than directives gets it on a line of its own above
     * everything.
     */
    private fun insertionIndex(lines: List<String>): Int {
        lines.indexOfLast { it.tag() != null }.takeIf { it >= 0 }?.let { return it + 1 }
        var lastMetadataIndex = -1
        for ((index, line) in lines.withIndex()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith(SOURCE_COMMENT)) continue
            val directive = ChordProSyntax.matchDirective(trimmedLine) ?: break
            if (!directive.isMetadata) break
            lastMetadataIndex = index
        }
        return lastMetadataIndex + 1
    }

    /** The tag of a line that is a tag directive, null for every other line. */
    private fun String.tag() = ChordProSyntax.matchDirective(trim())?.let { ChordProSyntax.tag(it) }

    /** True for the directives a song is described by, as opposed to the ones that make up its body. */
    private val ChordProSyntax.Directive.isMetadata
        get() = ChordProSyntax.startOfEnvironment(name) == null && ChordProSyntax.endOfEnvironment(name) == null && name !in bodyNames

    private val bodyNames = setOf(
        "chorus", "comment", "c", "comment_italic", "ci", "comment_box", "cb", "new_page", "np", "new_physical_page",
        "npp", "column_break", "colb", "new_song", "ns",
    )

    private const val SOURCE_COMMENT = "#"
}
