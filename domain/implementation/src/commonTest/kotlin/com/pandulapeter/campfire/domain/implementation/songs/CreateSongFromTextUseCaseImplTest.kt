/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.songs

import com.pandulapeter.campfire.domain.implementation.RecordingSongRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class CreateSongFromTextUseCaseImplTest {

    @Test
    fun `the text is written exactly as it is, named by its title, subtitle and artist`() = runTest {
        val repository = RecordingSongRepository()
        val text = "{title: Title}\n{subtitle: Live}\n{artist: Artist}\n{capo: 2}\n[Am]Hello  [C]world"
        CreateSongFromTextUseCaseImpl(repository)(text)
        assertEquals(text, repository.text)
        assertEquals("Title (Live)" to "Artist", repository.name)
    }

    @Test
    fun `a text with no artist is named by its title alone`() = runTest {
        val repository = RecordingSongRepository()
        CreateSongFromTextUseCaseImpl(repository)("{title: Title}\n\n[G]Lyrics\n")
        assertEquals("Title" to "", repository.name)
    }
}
