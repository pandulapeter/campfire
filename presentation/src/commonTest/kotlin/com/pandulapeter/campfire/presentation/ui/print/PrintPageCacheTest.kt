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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PrintPageCacheTest {

    private val measured = mutableListOf<PrintPage>()
    private val cache = PrintPageCache { page -> page.texts.map { it.text }.also { measured += page } }

    private fun page(text: String) = PrintPage(texts = listOf(PrintText(text = text, x = 0f, y = 0f, style = PrintStyle(size = 10f))))

    @Test
    fun `a page drawn again is not laid out again`() {
        val page = page("Verse")
        val first = cache.of(page)
        assertSame(first, cache.of(page))
        assertEquals(listOf(page), measured)
    }

    @Test
    fun `a page past the bound evicts the one drawn longest ago`() {
        val pages = List(PRINT_PAGE_CACHE_SIZE + 1) { page("Page $it") }
        pages.take(PRINT_PAGE_CACHE_SIZE).forEach { cache.of(it) }
        // Drawn again, the first page is the most recent, so the second one is the one the next page pushes out.
        cache.of(pages[0])
        cache.of(pages.last())
        measured.clear()
        cache.of(pages[0])
        pages.drop(2).forEach { cache.of(it) }
        assertEquals(emptyList(), measured)
        cache.of(pages[1])
        assertEquals(listOf(pages[1]), measured)
    }

    @Test
    fun `an equal page made anew is laid out again`() {
        cache.of(page("Chorus"))
        cache.of(page("Chorus"))
        assertEquals(2, measured.size)
    }
}
