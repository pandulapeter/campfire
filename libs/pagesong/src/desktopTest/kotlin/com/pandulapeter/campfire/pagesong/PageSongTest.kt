/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.pagesong

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PageSongTest {

    private val weights = requireNotNull(PageSongTest::class.java.getResource("/tiny.bin")).readBytes()
    private val pageSong = PageSong(weights)

    @Test
    fun `an empty page holds no song and keeps nothing`() {
        val crop = pageSong.crop(emptyList(), PageLineSource.PAGE)

        assertEquals(PageCrop(holdsSong = false, keptLines = emptyList(), titleLine = null, artistLine = null, headerLines = emptyList(), scores = emptyList()), crop)
    }

    @Test
    fun `a page of one line is scored and kept whole for holding no song`() {
        val crop = pageSong.crop(listOf(PageLine("Amazing grace, how sweet the sound")), PageLineSource.PAGE)

        assertEquals(1, crop.scores.size)
        assertEquals(1f, PageLineLabel.entries.sumOf { crop.scores.single()[it].toDouble() }.toFloat(), 1e-5f)
        assertEquals(false, crop.holdsSong)
        assertEquals(listOf(0), crop.keptLines)
    }

    @Test
    fun `lines past the cap are neither read nor kept`() {
        val lines = List(PageSong.MAX_LINES + 20) { PageLine(if (it % 2 == 0) "G   D   Em   C" else "Line $it of the song") }
        val crop = pageSong.crop(lines, PageLineSource.PAGE)

        assertEquals(PageSong.MAX_LINES, crop.scores.size)
        if (crop.holdsSong) {
            assertTrue(crop.keptLines.all { it < PageSong.MAX_LINES })
        } else {
            assertEquals(lines.indices.toList(), crop.keptLines)
        }
        assertTrue(listOfNotNull(crop.titleLine, crop.artistLine).all { it < PageSong.MAX_LINES })
    }

    @Test
    fun `a line of spaces is blank as the gate strips it but one of a zero-width space is printed`() {
        val lines = listOf(PageLine(""), PageLine(" \t\u00A0 "), PageLine("\u200B"), PageLine("G"))

        assertEquals(setOf(0, 1), pageSong.blankLinesOf(lines))
    }

    @Test
    fun `a document line without a layout reads as body text`() {
        val withoutLayout = lineFeaturesOf(PageLine("G  D"), PageLineSource.DOCUMENT)
        val bodyText = lineFeaturesOf(
            PageLine("G  D", PageLineLayout(page = 0, column = null, size = 1f, isBold = false, isItalic = false, isMonospace = false, indent = 0f, isHeading = false)),
            PageLineSource.DOCUMENT,
        )

        assertEquals(bodyText.toList(), withoutLayout.toList())
    }

    @Test
    fun `a page reads its lines' marks and never a layout, and a document the reverse`() {
        val layout = PageLineLayout(page = 3, column = 1, size = 2f, isBold = true, isItalic = true, isMonospace = true, indent = 0.5f, isHeading = true)
        val marks = PageLineMarks(size = 2f, isBold = true, isItalic = false, isMonospace = false, isHeading = false, isLink = true, isNewBlock = true, isLandmark = false)

        assertEquals(lineFeaturesOf(PageLine("Sign in", marks = marks), PageLineSource.PAGE).toList(), lineFeaturesOf(PageLine("Sign in", layout, marks), PageLineSource.PAGE).toList())
        assertEquals(lineFeaturesOf(PageLine("Sign in", layout), PageLineSource.DOCUMENT).toList(), lineFeaturesOf(PageLine("Sign in", layout, marks), PageLineSource.DOCUMENT).toList())
        assertEquals(lineFeaturesOf(PageLine("Sign in"), PageLineSource.PAGE).toList(), lineFeaturesOf(PageLine("Sign in", layout), PageLineSource.PAGE).toList())
    }

    @Test
    fun `weights of another format or version or size are refused`() {
        assertFailsWith<IllegalArgumentException> { PageSong(ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { PageSong(weights.copyOf().also { it[0] = 'X'.code.toByte() }) }
        assertFailsWith<IllegalArgumentException> { PageSong(weights.copyOf().also { it[4] = 1 }) }
        assertFailsWith<IllegalArgumentException> { PageSong(weights.copyOf(weights.size - 1)) }
        assertFailsWith<IllegalArgumentException> { PageSong(weights.copyOf(weights.size + 1)) }
    }

    @Test
    fun `a repeated line is labelled as its neighbours make it rather than as its first copy`() {
        val lines = listOf(PageLine("Chorus"), PageLine("Home"), PageLine("Chorus"))
        val crop = pageSong.crop(lines, PageLineSource.PAGE)

        assertTrue(crop.scores[0] != crop.scores[2])
    }
}
