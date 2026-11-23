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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CropRuleTest {

    @Test
    fun `song and metadata lines are kept and clutter is dropped`() {
        val crop = cropOf(listOf(junk, song, song, metadata, song, song, junk), lineCount = 7, blankLines = emptySet())

        assertTrue(crop.holdsSong)
        assertEquals(listOf(1, 2, 3, 4, 5), crop.keptLines)
    }

    @Test
    fun `a line with a small chance of being the song is kept but one with less is not`() {
        val unsure = scores(junk = 0.97f, song = 0.015f, metadata = 0.015f)
        val sure = scores(junk = 0.985f, song = 0.01f, metadata = 0.005f)
        val crop = cropOf(listOf(song, song, unsure, song, sure, song), lineCount = 6, blankLines = emptySet())

        assertEquals(listOf(0, 1, 2, 3, 5), crop.keptLines)
    }

    @Test
    fun `a chance that rounds to the threshold keeps the line as in the gate`() {
        val crop = cropOf(listOf(song, song, song, song, scores(junk = 0.98004f, song = 0.01996f)), lineCount = 5, blankLines = emptySet())

        assertEquals(listOf(0, 1, 2, 3, 4), crop.keptLines)
    }

    @Test
    fun `a chance of several labels together is unsure where it rounds to the threshold as in the gate`() {
        assertTrue(scores(junk = 0.98004f, title = 0.01f, artist = 0.00996f).isUnsure(PageLineLabel.TITLE, PageLineLabel.ARTIST))
        assertFalse(scores(junk = 0.9805f, title = 0.01f, artist = 0.0095f).isUnsure(PageLineLabel.TITLE, PageLineLabel.ARTIST))
        assertFalse(scores(junk = 0.98f, title = 0.02f).isUnsure(PageLineLabel.ARTIST))
    }

    @Test
    fun `fewer than four song lines hold no song and every line is kept`() {
        val crop = cropOf(listOf(junk, song, song, song, metadata), lineCount = 7, blankLines = emptySet())

        assertFalse(crop.holdsSong)
        assertEquals((0 until 7).toList(), crop.keptLines)
    }

    @Test
    fun `lines kept only for being unsure do not count as the song`() {
        val unsure = scores(junk = 0.9f, song = 0.1f)
        val crop = cropOf(listOf(song, song, song, unsure, unsure), lineCount = 5, blankLines = emptySet())

        assertFalse(crop.holdsSong)
    }

    @Test
    fun `the title and the artist are the surest of the lines labelled so`() {
        val crop = cropOf(
            listOf(
                scores(title = 0.6f, junk = 0.4f),
                scores(title = 0.9f, junk = 0.1f),
                scores(artist = 0.7f, title = 0.3f),
                scores(junk = 0.5f, title = 0.45f, artist = 0.05f),
                song, song, song, song,
            ),
            lineCount = 8,
            blankLines = emptySet(),
        )

        assertEquals(1, crop.titleLine)
        assertEquals(2, crop.artistLine)
    }

    @Test
    fun `no line labelled as the title or the artist names neither`() {
        val crop = cropOf(listOf(song, song, song, song, junk), lineCount = 5, blankLines = emptySet())

        assertNull(crop.titleLine)
        assertNull(crop.artistLine)
    }

    @Test
    fun `a title and an artist before the song and set apart from it head it`() {
        val crop = cropOf(listOf(junk, title, artist, metadata, junk, song, song, song, song), lineCount = 9, blankLines = emptySet())

        assertEquals(listOf(1, 2), crop.headerLines)
    }

    @Test
    fun `a title after the song's first line stays in the song`() {
        assertEquals(emptyList(), cropOf(listOf(junk, metadata, song, song, title, song, song), lineCount = 7, blankLines = emptySet()).headerLines)
    }

    @Test
    fun `a title running straight into the song may be its first line and heads nothing`() {
        assertEquals(emptyList(), cropOf(listOf(junk, title, artist, song, song, song, song), lineCount = 7, blankLines = emptySet()).headerLines)
    }

    @Test
    fun `a title the model gives a chance of being the song stays in it`() {
        val unsureTitle = scores(title = 0.9f, song = 0.1f)
        val crop = cropOf(listOf(unsureTitle, artist, junk, song, song, song, song), lineCount = 7, blankLines = emptySet())

        assertEquals(listOf(1), crop.headerLines)
    }

    @Test
    fun `a page with no song has no header`() {
        assertEquals(emptyList(), cropOf(listOf(title, junk, song, song, song), lineCount = 5, blankLines = emptySet()).headerLines)
    }

    @Test
    fun `lines scored already are cropped by the same rule`() {
        val crop = PageCrop.of(listOf(junk, song, song, metadata, song, song, junk), blankLines = emptySet())

        assertTrue(crop.holdsSong)
        assertEquals(listOf(1, 2, 3, 4, 5), crop.keptLines)
        assertFalse(PageCrop.of(listOf(junk, song, song, song), blankLines = emptySet()).holdsSong)
        assertEquals(listOf(0, 1, 2, 3), PageCrop.of(listOf(junk, song, song, song), blankLines = emptySet()).keptLines)
    }

    @Test
    fun `blank lines the model calls the song do not make a song of too few printed ones`() {
        val crop = cropOf(listOf(junk, song, song, junk, song, song, junk), lineCount = 7, blankLines = setOf(1, 4))

        assertFalse(crop.holdsSong)
        assertEquals((0 until 7).toList(), crop.keptLines)
    }

    @Test
    fun `blank lines are left out of the kept lines for the caller to put back`() {
        val crop = cropOf(listOf(junk, song, song, song, song, song, song, junk), lineCount = 8, blankLines = setOf(1, 4))

        assertTrue(crop.holdsSong)
        assertEquals(listOf(2, 3, 5, 6), crop.keptLines)
    }

    @Test
    fun `a blank line the model calls the song is not where the song starts for its header`() {
        val crop = cropOf(listOf(title, song, junk, song, song, song, song), lineCount = 7, blankLines = setOf(1))

        assertEquals(listOf(0), crop.headerLines)
    }

    @Test
    fun `a blank line the model calls the title is never the title line`() {
        val surerBlank = cropOf(
            listOf(scores(title = 0.99f, junk = 0.01f), scores(title = 0.6f, junk = 0.4f), junk, song, song, song, song),
            lineCount = 7,
            blankLines = setOf(0),
        )
        val onlyBlank = cropOf(listOf(title, junk, song, song, song, song), lineCount = 6, blankLines = setOf(0))

        assertEquals(1, surerBlank.titleLine)
        assertNull(onlyBlank.titleLine)
    }

    private val junk = scores(junk = 1f)
    private val song = scores(song = 1f)
    private val metadata = scores(metadata = 1f)
    private val title = scores(title = 1f)
    private val artist = scores(artist = 1f)

    private fun scores(junk: Float = 0f, song: Float = 0f, title: Float = 0f, artist: Float = 0f, metadata: Float = 0f) =
        PageLineScores(floatArrayOf(junk, song, title, artist, metadata))
}
