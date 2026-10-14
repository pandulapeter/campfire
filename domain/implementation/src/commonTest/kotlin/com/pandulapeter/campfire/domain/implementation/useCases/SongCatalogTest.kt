/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag
import kotlin.test.Test
import kotlin.test.assertEquals

/** The tags the filter offers: one chip per word, whatever capitals each song wrote it in. */
class SongCatalogTest {

    @Test
    fun `two spellings of a tag are one tag, spelled the way the first song by file name spells it`() {
        val songs = listOf(
            testSong("c.cho", tags = listOf("folk")),
            testSong("a.cho", tags = listOf("Folk", "Ballad")),
            testSong("b.cho", tags = listOf("FOLK")),
        )

        // Whichever order the repository lists the songs in, since a saved song moves to the end of its list.
        for (order in listOf(songs, songs.reversed())) {
            assertEquals(listOf(Tag("Folk", 3), Tag("Ballad", 1)), order.toTags(NormalizeTextUseCaseImpl()::invoke))
        }
    }

    @Test
    fun `tags are spelled by the first song by file name and counted once per song`() {
        val songs = listOf(
            testSong("c.cho", tags = listOf("christmas")),
            testSong("a.cho", tags = listOf("Christmas", "CHRISTMAS")),
            testSong("b.cho", tags = listOf("Rock", "christmas")),
            testSong("d.cho", tags = listOf("rock")),
            testSong("e.cho", tags = listOf("Solo")),
        )
        assertEquals(
            listOf(Tag(name = "Christmas", songCount = 3), Tag(name = "Rock", songCount = 2), Tag(name = "Solo", songCount = 1)),
            songs.toTags(NormalizeTextUseCaseImpl()::invoke),
        )
    }

    @Test
    fun `languages are most used first with the unknown ones last`() {
        val songs = listOf(
            testSong("a.cho"),
            testSong("b.cho"),
            testSong("c.cho"),
            testSong("d.cho", languages = listOf("hu")),
            testSong("e.cho", languages = listOf("en", "hu")),
            testSong("f.cho", languages = listOf("en")),
            testSong("g.cho", languages = listOf("hu")),
        )
        assertEquals(
            listOf(
                SongLanguage(code = "hu", songCount = 3),
                SongLanguage(code = "en", songCount = 2),
                SongLanguage(code = SongLanguage.UNKNOWN, songCount = 3),
            ),
            songs.toLanguages(),
        )
    }

    @Test
    fun `tags with equal counts sort without regard to accents`() {
        val songs = listOf(
            testSong("a.cho", tags = listOf("Ébredés")),
            testSong("b.cho", tags = listOf("Zene")),
            testSong("c.cho", tags = listOf("Ebony")),
        )
        assertEquals(listOf("Ebony", "Ébredés", "Zene"), songs.toTags(NormalizeTextUseCaseImpl()::invoke).map { it.name })
    }

    @Test
    fun `a decomposed spelling is the same tag`() {
        val songs = listOf(
            testSong("a.cho", tags = listOf("cafe\u0301")),
            testSong("b.cho", tags = listOf("Café")),
        )
        assertEquals(listOf(2), songs.toTags(NormalizeTextUseCaseImpl()::invoke).map { it.songCount })
    }
}
