/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.model.library.LibraryFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedTextNameTest {

    @Test
    fun `a page title loses the site and what kind of page it is`() {
        assertEquals("wonderwall_by_oasis", sharedTextName("WONDERWALL CHORDS (ver 2) by Oasis @ Ultimate-Guitar.Com"))
        assertEquals("oasis_wonderwall", sharedTextName("Oasis - Wonderwall Chords | Ultimate Guitar"))
        assertEquals("wonderwall", sharedTextName("Wonderwall Lyrics and Chords - example.com"))
        assertEquals("omega_gyongyhaju_lany", sharedTextName("Omega - Gyöngyhajú lány akkordok"))
    }

    @Test
    fun `a subject's title keeps its case and single spaces, and an artist only after the page kind's by`() {
        assertEquals(
            SharedTextTitle(title = "Riverlight", artist = "The Invented Band"),
            sharedTextTitle("Riverlight   Chords by The Invented Band @ Ultimate-Guitar.Com"),
        )
        assertEquals(SharedTextTitle(title = "Stand by Me", artist = "Ben E. King"), sharedTextTitle("Stand by Me Chords by Ben E. King"))
        assertEquals(SharedTextTitle(title = "WONDERWALL", artist = "Oasis"), sharedTextTitle("WONDERWALL CHORDS (ver 2) by Oasis @ Ultimate-Guitar.Com"))
        assertEquals(SharedTextTitle(title = "Stand by Me", artist = null), sharedTextTitle("Stand by Me Lyrics"))
        assertEquals(SharedTextTitle(title = "Oasis - Wonderwall", artist = null), sharedTextTitle("Oasis - Wonderwall Chords | Ultimate Guitar"))
        assertNull(sharedTextTitle(null))
    }

    @Test
    fun `a subject that is a plain title keeps its words`() {
        assertEquals("amazing_grace", sharedTextName("Amazing Grace"))
    }

    @Test
    fun `a page word inside a title is the title's own, and one ending a part of it is the page's`() {
        assertEquals(
            SharedTextTitle(title = "The Lost Chord", artist = "The Invented Band"),
            sharedTextTitle("The Lost Chord Chords by The Invented Band @ Ultimate-Guitar.Com"),
        )
        assertEquals("Three Chords and the Truth", sharedTextTitle("Three Chords and the Truth Chords by The Invented Band")?.title)
        assertEquals("Riverlight - The Invented Band", sharedTextTitle("Riverlight Chords - The Invented Band")?.title)
        assertEquals("Riverlight", sharedTextTitle("Chords: Riverlight")?.title)
    }

    @Test
    fun `a subject of nothing but those words is kept rather than lost`() {
        assertEquals("chords", sharedTextName("Chords"))
    }

    @Test
    fun `a subject with nothing usable is no name`() {
        assertNull(sharedTextName(null))
        assertNull(sharedTextName(" / \u0007 "))
    }

    @Test
    fun `the first plain line skips rows of chords and addresses`() {
        assertEquals("amazing_grace", "{t: x}\nEm7  G  Dsus4\nhttps://example.com/song\n[Verse]\nAmazing Grace\nHow sweet".firstPlainLine())
        assertEquals("a_day_in_the_life", "A day in the life".firstPlainLine())
        assertNull("Am  C\n\nG".firstPlainLine())
    }

    @Test
    fun `a name is the one the library would write, and stays it when normalized again`() {
        listOf("evil‮\ttxt", "a​ \n b", "Ég a napmelegtől", "Ода к радости", "x".repeat(300)).forEach { text ->
            val name = text.cleanedForFileName()
            assertEquals(LibraryFiles.normalizedName(text), name)
            assertEquals(name, name?.let(LibraryFiles::normalizedName))
        }
    }

    @Test
    fun `a text with no letter or digit is no name rather than the library's fallback`() {
        assertNull("😀 -- !".cleanedForFileName())
    }
}
