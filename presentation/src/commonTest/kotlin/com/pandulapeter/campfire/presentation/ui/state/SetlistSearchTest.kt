/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.useCases.NormalizeSearchTextUseCase
import com.pandulapeter.campfire.presentation.ui.search.SearchableSong
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetlistSearchTest {

    private val normalize = object : NormalizeSearchTextUseCase {
        override fun invoke(text: String) = text.lowercase()
    }

    private val library = mapOf(
        "present.cho" to SearchableSong(song = song("present.cho"), title = "wonderwall", artist = "oasis", tags = listOf("britpop")),
    )

    @Test
    fun `a setlist answers by its title or its description`() {
        assertTrue(setlist(title = "Friday gig").matches("friday"))
        assertTrue(setlist(description = "The wedding on Saturday").matches("wedding"))
    }

    @Test
    fun `a setlist answers by the title, the artist or a tag of a song it holds`() {
        val setlist = setlist(entries = listOf("present.cho"))
        assertTrue(setlist.matches("wonder"))
        assertTrue(setlist.matches("oasis"))
        assertTrue(setlist.matches("britpop"))
    }

    @Test
    fun `an entry whose file is missing is never found by its file name`() {
        assertFalse(setlist(entries = listOf("missing_song.cho")).matches("missing"))
    }

    private fun Setlist.matches(query: String) = matchesSearch(normalizedQuery = query, songs = library, normalizeSearchText = normalize)

    private fun setlist(title: String = "Set", description: String = "", entries: List<String> = emptyList()) = Setlist(
        fileName = "set.setlist.json",
        title = title,
        description = description,
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = entries.map { Setlist.Entry(songFileName = it) },
        size = 0L,
    )

    private fun song(fileName: String) = Song(
        fileName = fileName, title = "", artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = false, canUpdateFileName = false, lastModified = 0L, size = 0L,
    )
}
