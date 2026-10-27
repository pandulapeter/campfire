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

import kotlin.math.abs

/**
 * Which pages of a document open a song. A songbook sets every song's title in one type, so a later page opens a song
 * only where its first line is set in the type of the document's first title: a song running onto another page keeps
 * it whole even where that page starts with a line [ChordSheetConverter] would take for a title on its own, such as a
 * chorus set in bold. A page repeating the title of the song it continues (`Title (cont.)`) never opens one.
 */
internal object ChordSheetPageSplit {
    /**
     * A page's first line that is not blank. [isTitle] is whether the converter would take it for a title on its page,
     * [isFollowedByBold] whether the first line under it that is no chord row is set in bold too.
     */
    class Lead(val text: String, val line: ChordSheet.Line, val isTitle: Boolean, val isFollowedByBold: Boolean)

    enum class Start {
        /** The page opens a song. */
        SONG,

        /** The page continues the song before it. */
        PAGE,

        /** The page continues the song before it under a repetition of its title, which is left out. */
        CONTINUATION,
    }

    /** One [Start] per page of [leads] (null for a blank page), the first always a [Start.SONG]. */
    fun starts(leads: List<Lead?>, medianSize: Double): List<Start> {
        val reference = leads.firstOrNull { it?.isTitle == true }
        val opens = leads.map { lead -> lead != null && reference != null && opensSong(lead, reference, medianSize) }
        val splits = opens.count { it } >= 2
        var title: Lead? = null
        return leads.mapIndexed { index, lead ->
            val current = title
            when {
                index == 0 -> Start.SONG.also { title = lead?.takeIf { it.isTitle } }
                lead != null && current != null && repeatsTitle(lead, current) ->
                    if (isRunningTitle(lead, current, medianSize)) Start.CONTINUATION else Start.PAGE
                splits && opens[index] -> Start.SONG.also { title = lead }
                else -> Start.PAGE
            }
        }
    }

    /**
     * A title set no larger than the body text is told apart only by being bold, which is how many sheets set a chorus;
     * a chorus has more bold lyrics under its first line, a title has its credit or the first verse.
     */
    private fun opensSong(lead: Lead, reference: Lead, medianSize: Double) = lead.isTitle && isSameType(lead.line, reference.line) &&
        (lead.line.isHeading || size(lead.line) > medianSize * 1.2 || !lead.isFollowedByBold)

    private fun isSameType(line: ChordSheet.Line, other: ChordSheet.Line): Boolean {
        val size = size(line)
        return line.isHeading == other.isHeading && abs(size - size(other)) < size * 0.05 && isBold(line) == isBold(other)
    }

    /** Whether [lead] names the song [title] opened: its words, then nothing but a continuation's or a page's marks. */
    private fun repeatsTitle(lead: Lead, title: Lead): Boolean {
        val words = words(lead.text)
        val titleWords = words(title.text)
        return titleWords.isNotEmpty() && words.take(titleWords.size) == titleWords &&
            words.drop(titleWords.size).all { it in continuationWords || it.all(Char::isDigit) }
    }

    /**
     * A repeated title is left out when it is marked as one or set in the title's type. Bare and in the body's type it
     * is as likely the song's first lyric on the page, a chorus singing the title, which is kept.
     */
    private fun isRunningTitle(lead: Lead, title: Lead, medianSize: Double) = words(lead.text).size > words(title.text).size ||
        isSameType(lead.line, title.line) && (lead.line.isHeading || size(lead.line) > medianSize * 1.2)

    private fun size(line: ChordSheet.Line) = line.spans.filter { it.text.isNotBlank() }.maxOfOrNull { it.size } ?: 0.0

    private fun isBold(line: ChordSheet.Line) = line.spans.filter { it.text.isNotBlank() }.let { spans -> spans.isNotEmpty() && spans.all { it.isBold } }

    private fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private val continuationWords = setOf(
        "cont", "contd", "continued", "continuation", "page", "p", "pg",
        "folyt", "folytatás", "oldal", "fortsetzung", "seite", "suite", "continuare", "pagina", "página",
    )
}
