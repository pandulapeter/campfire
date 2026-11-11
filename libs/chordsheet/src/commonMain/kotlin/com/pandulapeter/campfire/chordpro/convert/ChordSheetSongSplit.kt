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
 * Where a document's songs start. A songbook sets every song's title in one type, the type that opens more than one
 * song: a cover or a contents page set in a type of its own opens one at most, so the first title of the document is
 * not the type's measure. A song running onto another page keeps it whole even where that page starts with a line
 * [ChordSheetConverter] would take for a title on its own, such as a chorus set in bold, and a page repeating the title
 * of the song it continues (`Title (cont.)`) never opens one. A type standing out by its size opens a song on any line
 * of a page, as a typeset songbook runs one song after another; a bold title of the body's size, which many sheets set a
 * chorus in too, only at the top of a page.
 *
 * Its contents tell a title where the type cannot: a line listed together with the others on a page of their own
 * ([ChordSheetIndexTitles]) opens a song where it stands on another page, in the titles' type where they have one, so a
 * title of the body's size with a bold chorus under it opens its song too. A songbook set in one type throughout tells
 * its titles by its contents alone.
 */
internal object ChordSheetSongSplit {
    /**
     * A page's line as the split sees it. [isTitleShaped] is whether nothing but its type keeps it from being a title
     * (no chord row, heading, or field), [isTitle] whether the converter takes it for a title on its page by its type, asked
     * only of a page's first line.
     */
    class Line(val text: String, val line: ChordSheet.Line, val isChordRow: Boolean, val isTitleShaped: Boolean, val isTitle: Boolean)

    /** A place in the document: the [line]th line of page [page]. */
    data class Place(val page: Int, val line: Int)

    /**
     * The song openings and the repeated titles left out, by place, the document's first line always an opening.
     * [numbers] is how many characters of an opening's text, from its first that is not blank, are the number the
     * songbook counts its songs by.
     */
    class Cuts(val opens: Set<Place>, val dropped: Set<Place>, val numbers: Map<Place, Int>)

    fun cuts(pages: List<List<Line>>, profile: ChordSheetProfile): Cuts {
        val leads = pages.mapIndexed { pageIndex, page -> page.indexOfFirst { it.text.isNotBlank() }.takeIf { it >= 0 }?.let { lead(pages, Place(pageIndex, it)) } }
        val index = ChordSheetIndexTitles.of(pages, ::key)
        val reference = reference(leads.filterNotNull().filter { it.line.isTitle }, profile, index?.titles.orEmpty())
        val typed = reference?.let { typedOpenings(pages, leads, it, profile) }.orEmpty()
        val byType = typed.distinctBy { words(it.line.text) }.size >= 2
        // An index of artists names the credit under each title too, which is never in the titles' type, and an entry
        // wrapped by its column names the rest of a title wrapped by its own.
        val listed = index?.titles.orEmpty().filter { place ->
            reference == null || !byType || isSameType(pages[place.page][place.line].line, reference.line.line) && !isWrapped(pages, place, reference)
        }
        val openings = (if (byType) typed.map { it.place } else emptyList()).toSet() + listed
        val opens = mutableSetOf(Place(0, 0))
        val dropped = mutableSetOf<Place>()
        var title = leads.firstOrNull()?.takeIf { it.line.isTitle || it.place in openings }
        pages.indices.forEach { pageIndex ->
            val lead = leads[pageIndex] ?: return@forEach
            val current = title
            when {
                pageIndex == 0 -> Unit
                current != null && repeatsTitle(lead, current) -> if (isRunningTitle(lead, current, profile)) dropped += lead.place
                lead.place in openings || index != null && pageIndex in index.pages && pageIndex - 1 !in index.pages -> {
                    opens += lead.place
                    title = lead
                }
            }
            pages[pageIndex].indices.filter { it > lead.place.line }.map { Place(pageIndex, it) }.filter { it in openings }.forEach { place ->
                val opening = lead(pages, place)
                if (title?.let { repeatsTitle(opening, it) } != true) {
                    opens += place
                    title = opening
                }
            }
        }
        return Cuts(opens, dropped, numbers(pages, opens))
    }

    /**
     * The numbers the titles open with where the songbook counts its songs: the songs after the front matter, whatever
     * its title starts with, each have one, rising from song to song, by one at most steps (a song the reading lost
     * skips one). Titles that merely start with a number (`7 Years`, `99 Problems`) do not count like that, and keep it.
     */
    private fun numbers(pages: List<List<Line>>, opens: Set<Place>): Map<Place, Int> {
        // The document's first place is an opening even where its first page has no line at all (a PDF's blank cover).
        val songs = opens.sortedWith(compareBy({ it.page }, { it.line })).map { place ->
            place to pages.getOrNull(place.page)?.getOrNull(place.line)?.let { number(it.text) }
        }
        val counted = mutableListOf<Pair<Place, Pair<Int, Int>>>()
        for ((place, number) in songs.asReversed()) {
            if (number == null || counted.lastOrNull()?.let { it.second.first <= number.first } == true) break
            counted += place to number
        }
        val steps = counted.zipWithNext { next, previous -> next.second.first - previous.second.first }
        if (counted.size < 3 || steps.count { it == 1 } * 5 < steps.size * 4) return emptyMap()
        return counted.associate { (place, number) -> place to number.second }
    }

    /** The number [text] opens with (`12`, `12.`, `12)`) and its length with the blanks after it, where words follow. */
    private fun number(text: String): Pair<Int, Int>? {
        val digits = text.takeWhile { it in '0'..'9' }
        if (digits.isEmpty() || digits.length > 4) return null
        val end = digits.length + if (text.getOrNull(digits.length) == '.' || text.getOrNull(digits.length) == ')') 1 else 0
        val blanks = text.drop(end).takeWhile(Char::isWhitespace).length
        return if (blanks == 0 || end + blanks == text.length) null else digits.toInt() to end + blanks
    }

    /** A line with what the type rules ask of the lines under it. */
    private class Lead(val place: Place, val line: Line, val isFollowedByBold: Boolean)

    private fun lead(pages: List<List<Line>>, place: Place): Lead {
        val page = pages[place.page]
        val next = page.drop(place.line + 1).firstOrNull { it.text.isNotBlank() && !it.isChordRow }
        return Lead(place, page[place.line], next != null && isBold(next.line))
    }

    /**
     * The first title whose type opens two songs of different titles, else the document's first title, whose type then
     * opens no other song unless it opens one more page. Past the first title, a type of the body's size counts only
     * where the contents ([listed]) name two of its openings: a sheet sets a part's name (`Repeat Chorus`, `Middle
     * eight`) bold at the body's size as often as a title, and a contents page never lists one.
     */
    private fun reference(titles: List<Lead>, profile: ChordSheetProfile, listed: Set<Place>): Lead? {
        val first = titles.firstOrNull() ?: return null
        if (titles.count { opensSong(it, first, profile) } >= 2) return first
        return titles.firstOrNull { candidate ->
            val opened = titles.filter { opensSong(it, candidate, profile) }
            val standsOut = candidate.line.line.isHeading || profile.isLarger(size(candidate.line.line))
            (standsOut || opened.count { it.place in listed } >= 2) && opened.distinctBy { words(it.line.text) }.size >= 2
        } ?: first
    }

    /** The page leads, and where the type stands out by its size the lines anywhere on a page, that open a song. */
    private fun typedOpenings(pages: List<List<Line>>, leads: List<Lead?>, reference: Lead, profile: ChordSheetProfile): List<Lead> {
        val anywhere = reference.line.line.isHeading || profile.isLarger(size(reference.line.line))
        return pages.indices.flatMap { pageIndex ->
            val lead = leads[pageIndex] ?: return@flatMap emptyList()
            val page = pages[pageIndex]
            val first = listOfNotNull(lead.takeIf { opensSong(it, reference, profile) })
            if (!anywhere) return@flatMap first
            first + page.indices.filter { it > lead.place.line }.mapNotNull { lineIndex ->
                val line = page[lineIndex]
                val place = Place(pageIndex, lineIndex)
                if (line.isTitleShaped && isSameType(line.line, reference.line.line) && !isWrapped(pages, place, reference)) lead(pages, place) else null
            }
        }
    }

    /** Whether the line at [place] goes on with a title wrapped by its column, which it does in the title's type. */
    private fun isWrapped(pages: List<List<Line>>, place: Place, reference: Lead) =
        pages[place.page].subList(0, place.line).lastOrNull { it.text.isNotBlank() }?.let { isSameType(it.line, reference.line.line) } == true

    /**
     * A title set no larger than the body text is told apart only by being bold, which is how many sheets set a chorus;
     * a chorus has more bold lyrics under its first line, a title has its credit or the first verse.
     */
    private fun opensSong(lead: Lead, reference: Lead, profile: ChordSheetProfile) = lead.line.isTitle && isSameType(lead.line.line, reference.line.line) &&
        (lead.line.line.isHeading || profile.isLarger(size(lead.line.line)) || !lead.isFollowedByBold)

    private fun isSameType(line: ChordSheet.Line, other: ChordSheet.Line): Boolean {
        val size = size(line)
        return line.isHeading == other.isHeading && ChordSheetProfile.isSameSize(size(other), size) && isBold(line) == isBold(other)
    }

    /** Whether [lead] names the song [title] opened: its words, then nothing but a continuation's or a page's marks. */
    private fun repeatsTitle(lead: Lead, title: Lead): Boolean {
        val words = words(lead.line.text)
        val titleWords = words(title.line.text)
        return lead.place != title.place && titleWords.isNotEmpty() && words.take(titleWords.size) == titleWords &&
            words.drop(titleWords.size).all { it in continuationWords || it.all(Char::isDigit) }
    }

    /**
     * A repeated title is left out when it is marked as one or set in the title's type. Bare and in the body's type it
     * is as likely the song's first lyric on the page, a chorus singing the title, which is kept.
     */
    private fun isRunningTitle(lead: Lead, title: Lead, profile: ChordSheetProfile) = words(lead.line.text).size > words(title.line.text).size ||
        isSameType(lead.line.line, title.line.line) && (lead.line.line.isHeading || profile.isLarger(size(lead.line.line)))

    /**
     * What a contents entry and its song's title share: the words, leaving out the number a songbook counts its songs
     * by and the page or song number an index ends an entry in (`Song . . . . 12`). Null for a line of numbers alone.
     */
    private fun key(text: String): String? {
        val words = words(text)
        val start = if (words.firstOrNull()?.all(Char::isDigit) == true) 1 else 0
        val end = words.indexOfLast { word -> !word.all(Char::isDigit) } + 1
        return words.subList(start, maxOf(start, end)).takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun size(line: ChordSheet.Line) = line.spans.filter { it.text.isNotBlank() }.maxOfOrNull { it.size } ?: 0.0

    private fun isBold(line: ChordSheet.Line) = line.spans.filter { it.text.isNotBlank() }.let { spans -> spans.isNotEmpty() && spans.all { it.isBold } }

    private fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private val continuationWords = setOf(
        "cont", "contd", "continued", "continuation", "page", "p", "pg",
        "folyt", "folytatás", "oldal", "fortsetzung", "seite", "suite", "continuare", "pagina", "página",
    )
}
