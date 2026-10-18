/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

/**
 * What [build] makes of the last [PRINT_PAGE_CACHE_SIZE] pages asked for, the most recently asked first: the laid out
 * texts of the pages a renderer draws, which the preview draws again on every frame of a zoom or a fade.
 *
 * It is kept by page rather than by text, since a page of small type in several columns holds hundreds of texts and a
 * frame draws several pages: a frame draws at most the pager's current page and its neighbours, of two documents while
 * the options crossfade, which always fits, whereas a bound on texts that a frame outgrows misses every one of them.
 * Pages are matched by identity: [PrintPage] is a data class whose `equals` walks every text it holds, and a layout
 * made again makes new pages anyway, so the old ones simply age out.
 *
 * Not synchronized: the preview's renderer is only drawn on the main thread, and the layout and the export each make a
 * renderer of their own.
 */
internal class PrintPageCache<T>(private val build: (PrintPage) -> T) {

    private val entries = ArrayDeque<Pair<PrintPage, T>>()

    fun of(page: PrintPage): T {
        val index = entries.indexOfFirst { it.first === page }
        val entry = if (index >= 0) entries.removeAt(index) else page to build(page)
        entries.addFirst(entry)
        if (entries.size > PRINT_PAGE_CACHE_SIZE) entries.removeLast()
        return entry.second
    }
}

internal const val PRINT_PAGE_CACHE_SIZE = 8
