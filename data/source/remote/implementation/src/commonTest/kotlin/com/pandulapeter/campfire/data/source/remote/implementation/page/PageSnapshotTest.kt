/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.page

import com.pandulapeter.campfire.data.model.importing.LoadedPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageSnapshotTest {

    @Test
    fun `a page with text is read with its title and final address`() {
        val page = PageSnapshot(text = SONG, title = " Wonderwall Chords by Oasis ", address = "https://chords.example.com/wonderwall").toLoadedPage()

        assertEquals(LoadedPage.Read(text = SONG, title = "Wonderwall Chords by Oasis", address = "https://chords.example.com/wonderwall"), page)
    }

    @Test
    fun `a page without a title is read with none`() {
        val page = PageSnapshot(text = SONG, title = "", address = "https://chords.example.com/a").toLoadedPage()

        assertNull((page as LoadedPage.Read).title)
    }

    @Test
    fun `an empty page is not read`() {
        assertEquals(LoadedPage.NotRead(LoadedPage.NotRead.Reason.UNREADABLE), PageSnapshot(text = " \n\t", title = "Songs", address = "https://chords.example.com/a").toLoadedPage())
    }

    @Test
    fun `a bot check standing in for the page is not read`() {
        listOf(
            PageSnapshot(text = "chords.example.com\nVerifying you are human. This may take a few seconds.", title = "Just a moment..."),
            PageSnapshot(text = "Pardon Our Interruption\nAs you were browsing something about your browser made us think you were a bot.", title = "Pardon Our Interruption"),
            PageSnapshot(text = "Please complete the CAPTCHA below to continue.", title = "chords.example.com"),
            PageSnapshot(text = "Access Denied\nYou don't have permission to access this server.", title = "Access Denied"),
        ).forEach { snapshot ->
            assertTrue(snapshot.isBotCheck(), snapshot.title)
            assertEquals(LoadedPage.NotRead(LoadedPage.NotRead.Reason.UNREADABLE), snapshot.copy(address = "https://chords.example.com/a").toLoadedPage())
        }
    }

    @Test
    fun `a long page that names a captcha somewhere is still the site's own`() {
        val snapshot = PageSnapshot(text = SONG.repeat(80) + "\nThis form is protected by reCAPTCHA.", title = "Song", address = "https://chords.example.com/a")

        assertFalse(snapshot.isBotCheck())
        assertTrue(snapshot.isReadable)
    }

    @Test
    fun `a page that ended up off a public https host is not read`() {
        listOf("http://chords.example.com/a", "https://192.168.1.1/a", "https://router.local/a", "").forEach { address ->
            assertEquals(LoadedPage.NotRead(LoadedPage.NotRead.Reason.UNREADABLE), PageSnapshot(text = SONG, title = "Song", address = address).toLoadedPage(), address)
        }
    }

    @Test
    fun `what the script answers is decoded and anything else is no snapshot`() {
        assertEquals(
            PageSnapshot(text = "a\nb", title = "t", address = "https://x.example.com/"),
            PageSnapshot.decode("""{"text":"a\nb","title":"t","address":"https://x.example.com/","extra":1}"""),
        )
        assertNull(PageSnapshot.decode(null))
        assertNull(PageSnapshot.decode("not json"))
        assertNull(PageSnapshot.decode("[]"))
    }

    private companion object {
        const val SONG = "[Verse 1]\nEm7       G\nToday is gonna be the day\n"
    }
}
