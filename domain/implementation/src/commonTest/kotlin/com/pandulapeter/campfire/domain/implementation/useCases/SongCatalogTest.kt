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
}
