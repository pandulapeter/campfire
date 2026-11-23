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
        val memo = PageSongLinesMemo { lines -> asked += lines; PageSongLines(lines.indices.toList(), null, null, emptyList()) }

        val first = memo.songLinesOf(listOf("G   D", "Down by the river"))
        val second = memo.songLinesOf(listOf("G   D", "Down by the river"))

        assertSame(first, second)
        assertEquals(1, asked.size)
    }

    @Test
    fun `other lines are cropped again and replace the last`() = runTest {
        val asked = mutableListOf<List<String>>()
        val memo = PageSongLinesMemo { lines -> asked += lines; PageSongLines(lines.indices.toList(), null, null, emptyList()) }

        memo.songLinesOf(listOf("One"))
        memo.songLinesOf(listOf("Two"))
        memo.songLinesOf(listOf("One"))

        assertEquals(listOf(listOf("One"), listOf("Two"), listOf("One")), asked)
    }

    @Test
    fun `a page holding no song is remembered as such`() = runTest {
        var runs = 0
        val memo = PageSongLinesMemo { runs++; null }

        assertNull(memo.songLinesOf(listOf("Menu")))
        assertNull(memo.songLinesOf(listOf("Menu")))
        assertEquals(1, runs)
    }

    @Test
    fun `a failed crop is not remembered`() = runTest {
        var runs = 0
        val memo = PageSongLinesMemo { runs++; error("The page model is not there") }

        assertFailsWith<IllegalStateException> { memo.songLinesOf(listOf("Menu")) }
        assertFailsWith<IllegalStateException> { memo.songLinesOf(listOf("Menu")) }
        assertEquals(2, runs)
    }
}
