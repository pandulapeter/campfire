/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

/**
 * The song a web page names in its title, or in the line it heads the song with: the song's name wrapped in what the
 * site says about itself, "Wonderwall Chords (ver 2) by Oasis @ Ultimate-Guitar.Com". Only ever a suggestion for a song
 * whose text declares no title or artist of its own.
 *
 * Its rule is apart from the converter's reading of a chord sheet's first line (`ChordSheetTitles`), which also splits
 * `Artist - Title`: a page title puts the dash in either order, so here a dash is never split.
 */
public data class PageTitle(
    val title: String,
    val artist: String?,
) {

    public companion object {

        /**
         * What [text] (a page's title, or its title line) names: the title, and the artist where it reads
         * "<title> <page kind> by <artist>". The site's own name, a version number, and the words saying what kind of page
         * it is, where they end the title or a part of it, are left out. A bare " by " is never split, since it is as often
         * part of the title ("Stand by Me"), and neither is a dash. Null for no text.
         */
        public fun of(text: String?): PageTitle? {
            val spaced = text?.replace(WHITESPACE, " ")?.trim()?.ifEmpty { null } ?: return null
            val named = spaced
                .substringBefore(" @ ")
                .substringBeforeLast(" | ")
                .replace(SITE_SUFFIX, "")
                .replace(VERSION, " ")
                .replace(WHITESPACE, " ")
                .trim()
            val byArtist = PAGE_KIND_BY_ARTIST.matchEntire(named)
            val title = (byArtist?.groupValues?.get(1) ?: named).withoutPageKind()
            // A title that was nothing but those words ("Chords") is still what the page called it.
            if (title.isEmpty()) return PageTitle(title = spaced, artist = null)
            return PageTitle(title = title, artist = byArtist?.groupValues?.get(2)?.withoutPageKind()?.ifEmpty { null })
        }

        /**
         * The artist a line naming only the artist gives ("Oasis", "by Oasis", "Artist: Oasis"), without the leading
         * "by" or the label of one or two words before a colon that a page puts there, in whatever language (a name
         * holds no colon that early), and the page kind words where they end it, as in a title. Null for a line with
         * nothing left.
         */
        public fun artistOf(line: String): String? {
            val spaced = line.replace(WHITESPACE, " ").trim()
            val name = BY.matchEntire(spaced)?.groupValues?.get(1) ?: LABELLED.matchEntire(spaced)?.groupValues?.get(1) ?: spaced
            val trimmed = name.trim { it.isWhitespace() || it in SEPARATORS }
            return trimmed.withoutPageKind().ifEmpty { trimmed }.ifEmpty { null }
        }

        private fun String.withoutPageKind() = replace(ENDING_PAGE_KIND, " ").replace(WHITESPACE, " ").trim { it.isWhitespace() || it in SEPARATORS }

        /** A trailing " - example.com" naming the site. */
        private val SITE_SUFFIX = Regex("""\s+[-–—]\s+\S+\.[a-z]{2,}\S*$""", RegexOption.IGNORE_CASE)
        private val VERSION = Regex("""\((?:ver|version|v)\.?\s*\d+\)""", RegexOption.IGNORE_CASE)
        private const val PAGE_KIND_WORD = """(?:chords|tabs?|lyrics|akkordok|akkord|dalszöveg|dalszövege)"""

        /** What kind of page it is, in the languages the app speaks: "Chords", "Lyrics and chords", "akkordok". */
        private val PAGE_KIND = Regex("""\b$PAGE_KIND_WORD(?:\s*(?:and|&|és|,)\s*$PAGE_KIND_WORD)*\b""", RegexOption.IGNORE_CASE)
        private const val SEPARATORS = "-–—:|,"

        /**
         * A [PAGE_KIND] where it ends the title or a part of it, before a separator, which is where a site puts it: the
         * same words inside a title ("Three Chords and the Truth") are the song's own.
         */
        private val ENDING_PAGE_KIND = Regex("""${PAGE_KIND.pattern}(?=\s*(?:$|[$SEPARATORS]))""", RegexOption.IGNORE_CASE)

        /** "Wonderwall Chords by Oasis": the one shape in which what follows " by " is known to be the artist. */
        private val PAGE_KIND_BY_ARTIST = Regex("""^(.+?)\s+${PAGE_KIND.pattern}\s+by\s+(.+)$""", RegexOption.IGNORE_CASE)
        private val BY = Regex("""^by\s+(.+)$""", RegexOption.IGNORE_CASE)
        private val LABELLED = Regex("""^\p{L}+(?:\s\p{L}+)?\s?:\s*(.+)$""")
        private val WHITESPACE = Regex("""\s+""")
    }
}
