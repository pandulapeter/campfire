/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.chordpro.edit.ChordProHeader
import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.ChordProTime

/**
 * Every metadata directive a song describes itself with, described once: the parser, the highlighter, the prettifier
 * and the field editor all read what a kind is from here, so that what one of them accepts the others do too.
 *
 * The entries are in the order a header reads best in — what the song is called, who made it, what record it came on
 * and what its sleeve looks like, and how it is played, with the repeatable ones at the end — so [ordinal] is a kind's
 * rank in a header (see [ChordProHeaderLayout.metadataOrder]).
 */
internal enum class MetadataKind(
    /** The one name the app knows the kind by, see [ChordProHeaderLayout.metadataKind]. */
    val longName: String,
    /** The short spellings ChordPro defines for it (`t` for `title`), read as the long one. */
    val aliases: Set<String> = emptySet(),
    /** Whether the spec defines `{meta: name value}` to mean exactly the standalone `{name: value}`, see [ChordProMetaItems.standardMeta]. */
    val isStandardMeta: Boolean = false,
    /** Whether a song may declare it more than once, see [ChordProHeader.repeatableMetadata]. */
    val isRepeatable: Boolean = false,
    /** Whether a later line of it is a change of the song's timing from where it stands, see [ChordProHeader.changeableMetadata]. */
    val isTimingChange: Boolean = false,
    /**
     * Whether its lines in the body are kept by the field editor as changes mid-song rather than dropped as duplicates.
     * Wider than [isTimingChange]: a later `{key}` is read past by the parser, but it is still the user's line.
     */
    val isKeptInBody: Boolean = false,
    /** Whether the parser can use a value of it, which it reads past where it cannot and the editor marks as unreadable. */
    val isReadable: (String) -> Boolean = { true },
) {
    TITLE(longName = "title", aliases = setOf("t"), isStandardMeta = true),
    SUBTITLE(longName = "subtitle", aliases = setOf("st"), isStandardMeta = true),
    ARTIST(longName = "artist", isStandardMeta = true),
    COMPOSER(longName = "composer", isStandardMeta = true),
    LYRICIST(longName = "lyricist", isStandardMeta = true),
    ALBUM(longName = "album", isStandardMeta = true),
    COVER(longName = ChordProMetaItems.COVER_NAME),
    YEAR(longName = "year", isStandardMeta = true),
    KEY(longName = ChordProVocabulary.KEY, isStandardMeta = true, isKeptInBody = true),
    CAPO(longName = "capo", isStandardMeta = true, isReadable = { value -> value.toIntOrNull()?.let { it >= 0 } == true }),
    TEMPO(
        longName = ChordProVocabulary.TEMPO,
        isStandardMeta = true,
        isTimingChange = true,
        isKeptInBody = true,
        isReadable = { ChordProTempo.parse(it) != null },
    ),
    TIME(
        longName = ChordProVocabulary.TIME,
        isStandardMeta = true,
        isTimingChange = true,
        isKeptInBody = true,
        isReadable = { ChordProTime.parse(it) != null },
    ),
    DURATION(longName = "duration", isStandardMeta = true, isReadable = { ChordProDuration.parse(it) != null }),
    TAG(longName = ChordProMetaItems.TAG_NAME, isRepeatable = true),
    LANGUAGE(longName = ChordProMetaItems.LANGUAGE_NAME, aliases = setOf(ChordProMetaItems.LANGUAGE_SHORT_NAME), isRepeatable = true),
    LINK(longName = ChordProMetaItems.LINK_NAME, isRepeatable = true),
    ;

    companion object {

        private val byName = entries.associateBy { it.longName }

        /** The kind whose long name is [longName], or null for every other directive name. */
        fun of(longName: String) = byName[longName]

        /** Every short spelling, under the long name it stands for. */
        val aliasLongNames = entries.flatMap { kind -> kind.aliases.map { it to kind.longName } }.toMap()
    }
}
