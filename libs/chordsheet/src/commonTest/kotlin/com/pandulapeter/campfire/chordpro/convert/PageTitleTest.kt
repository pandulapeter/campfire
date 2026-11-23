/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageTitleTest {

    @Test
    fun `a page title keeps its case and single spaces and an artist only after the page kind's by`() {
        assertEquals(
            PageTitle(title = "Riverlight", artist = "The Invented Band"),
            PageTitle.of("Riverlight   Chords by The Invented Band @ Ultimate-Guitar.Com"),
        )
        assertEquals(PageTitle(title = "Stand by Me", artist = "Ben E. King"), PageTitle.of("Stand by Me Chords by Ben E. King"))
        assertEquals(PageTitle(title = "WONDERWALL", artist = "Oasis"), PageTitle.of("WONDERWALL CHORDS (ver 2) by Oasis @ Ultimate-Guitar.Com"))
        assertEquals(PageTitle(title = "Stand by Me", artist = null), PageTitle.of("Stand by Me Lyrics"))
        assertEquals(PageTitle(title = "Oasis - Wonderwall", artist = null), PageTitle.of("Oasis - Wonderwall Chords | Ultimate Guitar"))
        assertNull(PageTitle.of(null))
        assertNull(PageTitle.of("  "))
    }

    @Test
    fun `a page word inside a title is the title's own and one ending a part of it is the page's`() {
        assertEquals(
            PageTitle(title = "The Lost Chord", artist = "The Invented Band"),
            PageTitle.of("The Lost Chord Chords by The Invented Band @ Ultimate-Guitar.Com"),
        )
        assertEquals("Three Chords and the Truth", PageTitle.of("Three Chords and the Truth Chords by The Invented Band")?.title)
        assertEquals("Riverlight - The Invented Band", PageTitle.of("Riverlight Chords - The Invented Band")?.title)
        assertEquals("Riverlight", PageTitle.of("Chords: Riverlight")?.title)
    }

    @Test
    fun `a title nothing but page words is kept rather than lost`() {
        assertEquals(PageTitle(title = "Chords", artist = null), PageTitle.of("Chords"))
    }

    @Test
    fun `a page's title line is read as its title is`() {
        assertEquals(PageTitle(title = "Wonderwall", artist = "Oasis"), PageTitle.of("Wonderwall Chords by Oasis"))
        assertEquals(PageTitle(title = "Hotel California", artist = null), PageTitle.of("Hotel California Chords"))
        assertEquals(PageTitle(title = "Stand by Me", artist = null), PageTitle.of("Stand by Me"))
        assertEquals(PageTitle(title = "by Eagles", artist = null), PageTitle.of("by Eagles"))
    }

    @Test
    fun `an artist line loses a leading by or a short label or the page kind and keeps any other name whole`() {
        assertEquals("Eagles", PageTitle.artistOf("by Eagles"))
        assertEquals("Oasis", PageTitle.artistOf("  Artist:  Oasis "))
        assertEquals("Omega", PageTitle.artistOf("Előadó: Omega"))
        assertEquals("Ben E. King", PageTitle.artistOf("Performed by: Ben E. King"))
        assertEquals("Stand by Me", PageTitle.artistOf("Stand by Me"))
        assertEquals("Simon & Garfunkel", PageTitle.artistOf("Simon & Garfunkel"))
        assertEquals("Words and music: Someone", PageTitle.artistOf("Words and music: Someone"))
        assertEquals("Kispál és a Borz", PageTitle.artistOf("Kispál és a Borz Chords & Tabs"))
        assertNull(PageTitle.artistOf(" - "))
    }
}
