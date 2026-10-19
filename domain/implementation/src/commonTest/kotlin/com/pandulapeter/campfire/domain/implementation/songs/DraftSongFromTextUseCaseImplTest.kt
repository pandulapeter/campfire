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

import kotlin.test.Test
import kotlin.test.assertEquals

class DraftSongFromTextUseCaseImplTest {

    private val draftSongFromText = DraftSongFromTextUseCaseImpl()

    private fun draft(text: String, suggestedTitle: String? = null, suggestedArtist: String? = null) = draftSongFromText(
        text = text,
        placeholderTitle = "New song",
        placeholderArtist = "Unknown artist",
        suggestedTitle = suggestedTitle,
        suggestedArtist = suggestedArtist,
    )

    @Test
    fun `a text with no header gets the placeholders and the template's lines set apart from the song`() {
        assertEquals(
            "{title: New song}\n{artist: Unknown artist}\n{key: }\n{capo: 0}\n{tempo: 120}\n{time: 4/4}\n\n[G]Lyrics\n",
            draft(text = "[G]Lyrics\n"),
        )
    }

    @Test
    fun `what the text declares is kept and only the missing lines are added`() {
        assertEquals(
            "{title: Real title}\n{artist: Unknown artist}\n{key: }\n{capo: 3}\n{tempo: 120}\n{time: 4/4}\n\n[G]Lyrics\n",
            draft(text = "{title: Real title}\n{capo: 3}\n\n[G]Lyrics\n"),
        )
    }

    @Test
    fun `a blank title or artist is given its placeholder`() {
        assertEquals(
            "{title: New song}\n{artist: Real artist}\n{key: }\n{capo: 0}\n{tempo: 120}\n{time: 4/4}\n\n[G]Lyrics\n",
            draft(text = "{title: }\n{artist: Real artist}\n\n[G]Lyrics\n"),
        )
    }

    @Test
    fun `a shared page title titles a song that declares none`() {
        assertEquals(
            "{title: Riverlight}\n{artist: The Invented Band}\n{key: }\n{capo: 0}\n{tempo: 120}\n{time: 4/4}\n\n[G]Lyrics\n",
            draft(text = "[G]Lyrics\n", suggestedTitle = "Riverlight", suggestedArtist = "The Invented Band"),
        )
        assertEquals(
            "{title: Real title}\n{artist: The Invented Band}\n{key: }\n{capo: 0}\n{tempo: 120}\n{time: 4/4}\n\n[G]Lyrics\n",
            draft(text = "{title: Real title}\n\n[G]Lyrics\n", suggestedTitle = "Riverlight", suggestedArtist = "The Invented Band"),
        )
    }
}
