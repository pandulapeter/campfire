/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songs

import com.pandulapeter.campfire.data.model.song.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RandomPicksTest {

    private val library = (1..40).map(::song)

    @Test
    fun `a list of twenty songs has no picks, one of twenty one has five of its own`() {
        assertEquals(emptyList(), randomPicks(library.take(20), seed = 1L))
        val picks = randomPicks(library.take(21), seed = 1L)
        assertEquals(5, picks.toSet().size)
        assertTrue(library.take(21).containsAll(picks))
    }

    @Test
    fun `a seed picks the same songs whatever order the list is in, and another seed others`() {
        val picks = randomPicks(library, seed = 1L)
        assertEquals(picks, randomPicks(library.reversed(), seed = 1L))
        assertNotEquals(picks, randomPicks(library, seed = 2L))
    }

    @Test
    fun `a song added changes the picks only where it outranks one of them`() {
        val before = randomPicks(library, seed = 3L)
        val after = randomPicks(library + song(41), seed = 3L)
        val displaced = before - after.toSet()
        assertTrue(displaced.size <= 1)
        assertEquals(before - displaced.toSet(), after - song(41))
    }

    private fun song(number: Int) = Song(
        fileName = "song_$number.cho", title = "Song $number", artist = "", key = null, transpose = 0,
        tags = emptyList(), languages = emptyList(), coverArtUrl = null, hasChords = false,
        canUpdateFileName = false, lastModified = 0L, size = 0L,
    )
}
