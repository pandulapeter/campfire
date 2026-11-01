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
 * A songbook's titles found through its contents, for a book set in one type throughout. A contents page is one whose
 * lines are mostly found again on other pages, at least three, on three pages or more: a page of a song repeating the
 * one before it, a chorus printed again, points at one page alone, and a chorus's lines each recur on every page it is
 * printed on, where an entry names one or two. Each entry is the title of the first line naming it that stands under no
 * chord row (a lyric singing the title stands under one), on a page that is no contents page, the top of a page before
 * any other place.
 */
internal class ChordSheetIndexTitles(val pages: Set<Int>, val titles: Set<ChordSheetSongSplit.Place>) {
    companion object {
        fun of(pages: List<List<ChordSheetSongSplit.Line>>, key: (String) -> String?): ChordSheetIndexTitles? {
            val entries = pages.mapIndexed { pageIndex, page ->
                page.indices.mapNotNull { lineIndex ->
                    val line = page[lineIndex]
                    if (!line.isTitleShaped) return@mapNotNull null
                    key(line.text)?.let { Entry(ChordSheetSongSplit.Place(pageIndex, lineIndex), it, page.subList(0, lineIndex).lastOrNull { it.text.isNotBlank() }) }
                }
            }
            val pagesByKey = entries.flatten().groupBy({ it.key }, { it.place.page }).mapValues { it.value.toSet() }
            val indexPages = entries.indices.filter { pageIndex ->
                val matched = entries[pageIndex].filter { entry -> pagesByKey.getValue(entry.key).any { it != pageIndex } }
                val pointed = matched.count { entry -> (pagesByKey.getValue(entry.key) - pageIndex).size <= 2 }
                matched.size >= 3 && matched.size * 2 >= entries[pageIndex].size && pointed * 2 > matched.size &&
                    matched.flatMap { entry -> pagesByKey.getValue(entry.key) - pageIndex }.toSet().size >= 3
            }.toSet()
            if (indexPages.isEmpty()) return null
            val listed = indexPages.flatMap { pageIndex -> entries[pageIndex].map { it.key } }.toSet()
            val candidates = entries.filterIndexed { pageIndex, _ -> pageIndex !in indexPages }.flatten()
                .filter { it.key in listed && it.previous?.isChordRow != true }
            val titles = candidates.groupBy { it.key }.values.map { named -> (named.firstOrNull { it.previous == null } ?: named.first()).place }.toSet()
            return if (titles.size >= 2) ChordSheetIndexTitles(indexPages, titles) else null
        }
    }

    private class Entry(val place: ChordSheetSongSplit.Place, val key: String, val previous: ChordSheetSongSplit.Line?)
}
