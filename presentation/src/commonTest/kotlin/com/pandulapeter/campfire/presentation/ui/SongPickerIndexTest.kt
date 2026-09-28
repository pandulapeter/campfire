/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag
import kotlin.test.Test
import kotlin.test.assertEquals

class SongPickerIndexTest {

    @Test
    fun tagsAreSpelledByTheFirstSongByFileNameAndCountedOncePerSong() {
        val options = pickerFilterOptions(
            listOf(
                song("c", tags = listOf("christmas")),
                song("a", tags = listOf("Christmas", "CHRISTMAS")),
                song("b", tags = listOf("Rock", "christmas")),
                song("d", tags = listOf("rock")),
                song("e", tags = listOf("Solo")),
            ),
        )
        assertEquals(
            listOf(Tag(name = "CHRISTMAS", songCount = 3), Tag(name = "Rock", songCount = 2), Tag(name = "Solo", songCount = 1)),
            options.tags,
        )
    }

    @Test
    fun aLibraryInOneLanguageHasNoLanguagesToOffer() {
        val options = pickerFilterOptions(listOf(song("a", languages = listOf("en")), song("b", languages = listOf("en"))))
        assertEquals(emptyList(), options.languages)
    }

    @Test
    fun languagesAreMostUsedFirstWithTheUnknownOnesLast() {
        val options = pickerFilterOptions(
            listOf(
                song("a"),
                song("b"),
                song("c"),
                song("d", languages = listOf("hu")),
                song("e", languages = listOf("en", "hu")),
                song("f", languages = listOf("en")),
                song("g", languages = listOf("hu")),
            ),
        )
        assertEquals(
            listOf(
                SongLanguage(code = "hu", songCount = 3),
                SongLanguage(code = "en", songCount = 2),
                SongLanguage(code = SongLanguage.UNKNOWN, songCount = 3),
            ),
            options.languages,
        )
    }

    private fun song(name: String, tags: List<String> = emptyList(), languages: List<String> = emptyList()) = Song(
        fileName = "$name.cho", title = name, artist = "", key = null, transpose = 0,
        tags = tags, languages = languages, coverArtUrl = null, hasChords = false,
        canUpdateFileName = false, lastModified = 0L, size = 0L,
    )
}
