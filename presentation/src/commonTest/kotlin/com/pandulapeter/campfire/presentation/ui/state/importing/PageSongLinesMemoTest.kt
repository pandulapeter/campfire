/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.domain.api.importing.PageSongLines
import com.pandulapeter.campfire.pagesong.PageLine
import com.pandulapeter.campfire.pagesong.PageLineMarks
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class PageSongLinesMemoTest {

    @Test
    fun `the same lines asked twice are cropped once`() = runTest {
        val asked = mutableListOf<List<String>>()
        val memo = PageSongLinesMemo { lines -> asked += lines.map { it.text }; PageSongLines(lines.indices.toList(), null, null, emptyList()) }

        val first = memo.songLinesOf(linesOf("G   D", "Down by the river"))
        val second = memo.songLinesOf(linesOf("G   D", "Down by the river"))

        assertSame(first, second)
        assertEquals(1, asked.size)
    }

    @Test
    fun `other lines are cropped again and replace the last`() = runTest {
        val asked = mutableListOf<List<String>>()
        val memo = PageSongLinesMemo { lines -> asked += lines.map { it.text }; PageSongLines(lines.indices.toList(), null, null, emptyList()) }

        memo.songLinesOf(linesOf("One"))
        memo.songLinesOf(linesOf("Two"))
        memo.songLinesOf(linesOf("One"))

        assertEquals(listOf(listOf("One"), listOf("Two"), listOf("One")), asked)
    }

    @Test
    fun `the same lines with other marks are cropped again`() = runTest {
        var runs = 0
        val memo = PageSongLinesMemo { runs++; null }
        val marks = PageLineMarks(size = 1f, isBold = false, isItalic = false, isMonospace = false, isHeading = false, isLink = true, isNewBlock = false, isLandmark = false)

        memo.songLinesOf(linesOf("Menu"))
        memo.songLinesOf(listOf(PageLine("Menu", marks = marks)))
        assertEquals(2, runs)
    }

    @Test
    fun `a page holding no song is remembered as such`() = runTest {
        var runs = 0
        val memo = PageSongLinesMemo { runs++; null }

        assertNull(memo.songLinesOf(linesOf("Menu")))
        assertNull(memo.songLinesOf(linesOf("Menu")))
        assertEquals(1, runs)
    }

    @Test
    fun `a failed crop is not remembered`() = runTest {
        var runs = 0
        val memo = PageSongLinesMemo { runs++; error("The page model is not there") }

        assertFailsWith<IllegalStateException> { memo.songLinesOf(linesOf("Menu")) }
        assertFailsWith<IllegalStateException> { memo.songLinesOf(linesOf("Menu")) }
        assertEquals(2, runs)
    }

    private fun linesOf(vararg texts: String) = texts.map(::PageLine)
}
