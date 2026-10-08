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

import com.pandulapeter.campfire.chordpro.ChordProDirectives.Directive
import com.pandulapeter.campfire.chordpro.ChordProDirectives.hasSelectorSuffix
import com.pandulapeter.campfire.chordpro.ChordProDirectives.matchDirective
import com.pandulapeter.campfire.chordpro.ChordProEnvironments.endOfEnvironment
import com.pandulapeter.campfire.chordpro.ChordProEnvironments.startOfEnvironment
import com.pandulapeter.campfire.chordpro.ChordProMetaItems.COVER_NAME
import com.pandulapeter.campfire.chordpro.ChordProMetaItems.LANGUAGE_NAME
import com.pandulapeter.campfire.chordpro.ChordProMetaItems.LANGUAGE_SHORT_NAME
import com.pandulapeter.campfire.chordpro.ChordProMetaItems.LINK_NAME
import com.pandulapeter.campfire.chordpro.ChordProMetaItems.TAG_NAME
import com.pandulapeter.campfire.chordpro.ChordProMetaItems.isCoverMeta
import com.pandulapeter.campfire.chordpro.ChordProMetaItems.isLanguageMeta
import com.pandulapeter.campfire.chordpro.ChordProMetaItems.isLinkMeta
import com.pandulapeter.campfire.chordpro.ChordProMetaItems.isTagMeta
import com.pandulapeter.campfire.chordpro.ChordProMetaItems.standardMeta

/** Where the header of a song ends, where its body begins and where a metadata directive added to it goes, see [metadataInsertionIndex]. */
internal object ChordProHeaderLayout {

    private const val SOURCE_COMMENT = "#"

    /**
     * The directives a song describes itself with, in the order a header reads best in: what the song is called, who
     * made it, what record it came on and what its sleeve looks like, and how it is played, with the three repeatable ones at the end. It is what
     * decides where a directive added to a file lands, see [metadataInsertionIndex].
     */
    val metadataOrder = listOf(
        "title", "subtitle", "artist", "composer", "lyricist", "album", COVER_NAME, "year", "key", "capo", "tempo", "time",
        "duration", TAG_NAME, LANGUAGE_NAME, LINK_NAME,
    )

    /**
     * The metadata directive a line declares, under the single name the app knows it by: the short spellings (`{t}`)
     * and the `{meta}` ones (`{meta: language en}`, `{meta: title …}`) fold into the long one, so that a file writing a
     * directive one way and a toolbar writing it another are understood to be talking about the same thing. Null for
     * everything else, the directives that make up the body and the custom metadata a song may carry included.
     */
    fun metadataKind(directive: Directive): String? = when {
        isTagMeta(directive) -> TAG_NAME
        isLanguageMeta(directive) -> LANGUAGE_NAME
        isCoverMeta(directive) -> COVER_NAME
        isLinkMeta(directive) -> LINK_NAME
        else -> {
            val name = standardMeta(directive)?.name ?: directive.name
            (metadataAliases[name] ?: name).takeIf { it in metadataOrder }
        }
    }

    internal val metadataAliases = mapOf(
        "t" to "title",
        "st" to "subtitle",
        LANGUAGE_SHORT_NAME to LANGUAGE_NAME,
    )

    /**
     * Where a directive of kind [name] goes when one is added to a file the user wrote.
     *
     * Right after the last directive of its own kind, wherever that is, so that the tags of a song stay together and
     * so do its languages — but for the kinds a song may change further down ([ChordProHeader.changeableMetadata]) only
     * the header's line counts, since one in the body is a change in the middle of the song. A kind the file does not declare yet goes into the block of directives the file opens
     * with, after the last one that comes before it in [metadataOrder]: a header written in that order stays in it,
     * and one the user has arranged some other way is left exactly as it is, since nothing already written is ever
     * moved. A file that opens with content rather than directives gets it on a line of its own above everything.
     */
    fun metadataInsertionIndex(lines: List<String>, name: String): Int {
        val searched = if (name in ChordProHeader.changeableMetadata) lines.subList(0, bodyStartIndex(lines)) else lines
        searched.indexOfLast { line -> matchDirective(line.trim())?.let(::metadataKind) == name }.takeIf { it >= 0 }?.let { return it + 1 }
        val rank = metadataOrder.indexOf(name).takeIf { it >= 0 } ?: metadataOrder.size
        var headerIndex = -1
        var insertionIndex = -1
        for ((index, line) in lines.withIndex()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith(SOURCE_COMMENT)) continue
            val directive = matchDirective(trimmedLine) ?: break
            if (!directive.isMetadata) break
            if (headerIndex < 0) headerIndex = index
            val otherRank = metadataKind(directive)?.let(metadataOrder::indexOf) ?: continue
            if (otherRank < rank) insertionIndex = index + 1
        }
        return if (insertionIndex >= 0) insertionIndex else headerIndex.coerceAtLeast(0)
    }

    /**
     * The index of the first line after the block of directives a song opens with (the comments and blank lines among
     * them included, but not the ones after it), or 0 for a song that opens with anything else.
     */
    fun headerEndIndex(lines: List<String>): Int {
        var end = 0
        for ((index, line) in lines.withIndex()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith(SOURCE_COMMENT)) continue
            val directive = matchDirective(trimmedLine) ?: break
            if (!directive.isMetadata) break
            end = index + 1
        }
        return end
    }

    /** True for the directives a song is described by, as opposed to the ones that make up its body. */
    private val Directive.isMetadata
        get() = startOfEnvironment(name) == null && endOfEnvironment(name) == null && name !in bodyNames

    /**
     * The directives [ChordProParser] makes a block of their own out of — a comment, a break, a chorus recall —
     * and which therefore cut whatever section they stand in into two, a run of tablature included.
     */
    val blockNames = setOf(
        "chorus", "comment", "c", "comment_italic", "ci", "comment_box", "cb", "highlight", "new_page", "np",
        "new_physical_page", "npp", "column_break", "colb",
    )

    private val bodyNames = blockNames + setOf("new_song", "ns")

    /** Whether [directive] begins the body of a song, see [bodyStartIndex]. */
    fun startsBody(directive: Directive) = startOfEnvironment(directive.name) != null || directive.name in blockNames

    /**
     * Where the body of the song [lines] make up begins, the way [ChordProParser] reads it: at the first line that is
     * not blank, a `#` comment or a directive that only sets something — the first line of lyrics, the first
     * `{start_of_…}` or the first directive that makes a block — or past the end of a song that has none. A directive
     * with a selector suffix puts nothing in the song, so it begins nothing either. What stands before it is the
     * header, which is what a song opens with: its `{transpose}`, and the first of its `{key}`, `{tempo}` and `{time}`.
     */
    fun bodyStartIndex(lines: List<String>): Int = lines.indexOfFirst { line ->
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty() || trimmedLine.startsWith(SOURCE_COMMENT)) return@indexOfFirst false
        val directive = matchDirective(trimmedLine) ?: return@indexOfFirst true
        !hasSelectorSuffix(directive.name) && startsBody(directive)
    }.takeIf { it >= 0 } ?: lines.size
}
