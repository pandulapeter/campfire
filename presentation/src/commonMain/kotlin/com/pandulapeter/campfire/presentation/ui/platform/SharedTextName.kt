/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * The name a shared text arrives under where the sender gave one: a browser's is the page's title, which is the song's
 * name wrapped in what the site says about itself - "Wonderwall Chords (ver 2) by Oasis @ Ultimate-Guitar.Com". The
 * site's own name, a version number and the words that say what kind of page it is, where they end the title or a part
 * of it, are left out, since the name only ever titles a song that declares no title of its own. Null where nothing usable is left.
 */
internal fun sharedTextName(subject: String?): String? =
    sharedTextTitle(subject)?.let { listOfNotNull(it.title, it.artist).joinToString(" by ") }?.cleanedForFileName()

/**
 * What [sharedTextName] reads out of a subject, as the sender wrote it rather than as a file name: the title, and the
 * artist where the subject reads "<title> <page kind> by <artist>". A bare " by " is never split, since it is as often
 * part of the title ("Stand by Me"), and neither is a dash, which sites put in either order. Null for no subject.
 */
internal fun sharedTextTitle(subject: String?): SharedTextTitle? {
    val spaced = subject?.replace(WHITESPACE, " ")?.trim()?.ifEmpty { null } ?: return null
    val named = spaced
        .substringBefore(" @ ")
        .substringBeforeLast(" | ")
        .replace(SITE_SUFFIX, "")
        .replace(VERSION, " ")
        .replace(WHITESPACE, " ")
        .trim()
    val byArtist = PAGE_KIND_BY_ARTIST.matchEntire(named)
    val title = (byArtist?.groupValues?.get(1) ?: named).withoutPageKind()
    // A title that was nothing but those words ("Chords") is still what the sender called it.
    if (title.isEmpty()) return SharedTextTitle(title = spaced, artist = null)
    return SharedTextTitle(title = title, artist = byArtist?.groupValues?.get(2)?.withoutPageKind()?.ifEmpty { null })
}

private fun String.withoutPageKind() = replace(ENDING_PAGE_KIND, " ").replace(WHITESPACE, " ").trim { it.isWhitespace() || it in SEPARATORS }

/**
 * Where a pasted chord sheet has its title, for a text that declares none: the first line that is neither a directive,
 * a section, a comment, a row of chords nor an address - the lines a chord sheet copied from a page most often starts
 * with, none of which names a song. Null where there is none.
 */
internal fun String.firstPlainLine() = lineSequence()
    .map { it.trim() }
    .firstOrNull { it.isNotEmpty() && it.first() !in "{[#" && !ChordSheetConverter.isChordLine(it) && !it.isLink() }
    ?.cleanedForFileName()

/**
 * A name for a file made of what arrived, by the rule every name the library writes follows
 * ([LibraryFiles.normalizedName]): lowercase words joined by underscores, accents folded, nothing that would break a
 * file name or hide in one, and a length capped by whole words. Null for a text with no letter or digit in it, which the
 * rule would otherwise answer with its own fallback name rather than with nothing.
 */
internal fun String.cleanedForFileName() = takeIf { it.any(Char::isLetterOrDigit) }?.let(LibraryFiles::normalizedName)

/** A trailing " - example.com" naming the site. */
private val SITE_SUFFIX = Regex("""\s+[-–—]\s+\S+\.[a-z]{2,}\S*$""", RegexOption.IGNORE_CASE)
private val VERSION = Regex("""\((?:ver|version|v)\.?\s*\d+\)""", RegexOption.IGNORE_CASE)
private const val PAGE_KIND_WORD = """(?:chords|tabs?|lyrics|akkordok|akkord|dalszöveg|dalszövege)"""

/** What kind of page it is, in the languages the app speaks: "Chords", "Lyrics and chords", "akkordok". */
private val PAGE_KIND = Regex("""\b$PAGE_KIND_WORD(?:\s*(?:and|&|és|,)\s*$PAGE_KIND_WORD)*\b""", RegexOption.IGNORE_CASE)

/**
 * A [PAGE_KIND] where it ends the title or a part of it, before a separator, which is where a site puts it: the same
 * words inside a title ("Three Chords and the Truth") are the song's own.
 */
private val ENDING_PAGE_KIND = Regex("""${PAGE_KIND.pattern}(?=\s*(?:$|[$SEPARATORS]))""", RegexOption.IGNORE_CASE)

/** "Wonderwall Chords by Oasis": the one shape in which what follows " by " is known to be the artist. */
private val PAGE_KIND_BY_ARTIST = Regex("""^(.+?)\s+${PAGE_KIND.pattern}\s+by\s+(.+)$""", RegexOption.IGNORE_CASE)
private val WHITESPACE = Regex("""\s+""")
private const val SEPARATORS = "-–—:|,"
