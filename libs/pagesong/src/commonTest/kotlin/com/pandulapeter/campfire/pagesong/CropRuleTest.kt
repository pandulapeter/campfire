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
        val crop = cropOf(listOf(junk, song, song, metadata, song, song, junk), lineCount = 7)

        assertTrue(crop.holdsSong)
        assertEquals(listOf(1, 2, 3, 4, 5), crop.keptLines)
        assertEquals(listOf(3), crop.metadataLines)
    }

    @Test
    fun `a line with a small chance of being the song is kept but one with less is not`() {
        val unsure = scores(junk = 0.97f, song = 0.015f, metadata = 0.015f)
        val sure = scores(junk = 0.985f, song = 0.01f, metadata = 0.005f)
        val crop = cropOf(listOf(song, song, unsure, song, sure, song), lineCount = 6)

        assertEquals(listOf(0, 1, 2, 3, 5), crop.keptLines)
    }

    @Test
    fun `a chance that rounds to the threshold keeps the line as in the gate`() {
        val crop = cropOf(listOf(song, song, song, song, scores(junk = 0.98004f, song = 0.01996f)), lineCount = 5)

        assertEquals(listOf(0, 1, 2, 3, 4), crop.keptLines)
    }

    @Test
    fun `fewer than four song lines hold no song and every line is kept`() {
        val crop = cropOf(listOf(junk, song, song, song, metadata), lineCount = 7)

        assertFalse(crop.holdsSong)
        assertEquals((0 until 7).toList(), crop.keptLines)
    }

    @Test
    fun `lines kept only for being unsure do not count as the song`() {
        val unsure = scores(junk = 0.9f, song = 0.1f)
        val crop = cropOf(listOf(song, song, song, unsure, unsure), lineCount = 5)

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
        )

        assertEquals(1, crop.titleLine)
        assertEquals(2, crop.artistLine)
    }

    @Test
    fun `no line labelled as the title or the artist names neither`() {
        val crop = cropOf(listOf(song, song, song, song, junk), lineCount = 5)

        assertNull(crop.titleLine)
        assertNull(crop.artistLine)
    }

    private val junk = scores(junk = 1f)
    private val song = scores(song = 1f)
    private val metadata = scores(metadata = 1f)

    private fun scores(junk: Float = 0f, song: Float = 0f, title: Float = 0f, artist: Float = 0f, metadata: Float = 0f) =
        PageLineScores(floatArrayOf(junk, song, title, artist, metadata))
}
