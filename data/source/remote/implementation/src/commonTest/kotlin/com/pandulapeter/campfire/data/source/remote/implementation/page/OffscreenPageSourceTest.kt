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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.LoadedPage
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OffscreenPageSourceTest {

    @Test
    fun `an address that is not a public https one is never opened`() = runTest {
        listOf("http://chords.example.com/a", "https://localhost/a", "https://10.0.0.1/a", "not an address").forEach { address ->
            val browser = FakeBrowser { PageLook(snapshot = null, isLoaded = false, hasFailed = false, isUnreachable = false) }

            assertEquals(UNREADABLE, source(browser).load(address), address)
            assertFalse(browser.isOpened, address)
        }
    }

    @Test
    fun `a page is taken once it has loaded and its text has stopped changing`() = runTest {
        var looks = 0
        val browser = FakeBrowser {
            looks++
            // Drawn by its scripts after the load: empty, then a part, then the whole song, which then stays.
            val text = when {
                looks < 3 -> ""
                looks < 5 -> "Loading"
                else -> SONG
            }
            PageLook(snapshot = PageSnapshot(text = text, title = "Song", address = ADDRESS), isLoaded = looks > 1, hasFailed = false, isUnreachable = false)
        }

        val page = source(browser).load(" $ADDRESS ")

        assertEquals(SONG, assertIs<LoadedPage.Read>(page).text)
        assertEquals(ADDRESS, browser.openedAddress)
        // Five looks to reach the song, then three quiet ones.
        assertEquals(8, looks)
        assertTrue(browser.isClosed)
    }

    @Test
    fun `a bot check that goes away by itself is waited out`() = runTest {
        var looks = 0
        val browser = FakeBrowser {
            looks++
            val snapshot = if (looks < 6) PageSnapshot(text = "Verifying you are human", title = "Just a moment...", address = ADDRESS) else PageSnapshot(text = SONG, title = "Song", address = ADDRESS)
            PageLook(snapshot = snapshot, isLoaded = true, hasFailed = false, isUnreachable = false)
        }

        assertIs<LoadedPage.Read>(source(browser).load(ADDRESS))
    }

    @Test
    fun `a bot check that stays is not read once the time is up`() = runTest {
        val browser = FakeBrowser { PageLook(snapshot = PageSnapshot(text = "Verifying you are human", title = "Just a moment...", address = ADDRESS), isLoaded = true, hasFailed = false, isUnreachable = false) }

        assertEquals(UNREADABLE, source(browser).load(ADDRESS))
        assertEquals(OffscreenPageSource.TIME_LIMIT.inWholeMilliseconds, currentTime)
        assertTrue(browser.isClosed)
    }

    @Test
    fun `a page that never stops loading is taken as it is once the time is up`() = runTest {
        val browser = FakeBrowser { PageLook(snapshot = PageSnapshot(text = SONG, title = "Song", address = ADDRESS), isLoaded = false, hasFailed = false, isUnreachable = false) }

        assertIs<LoadedPage.Read>(source(browser).load(ADDRESS))
        assertEquals(OffscreenPageSource.TIME_LIMIT.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `a page that failed to load is not read whatever its error page says`() = runTest {
        val browser = FakeBrowser { PageLook(snapshot = PageSnapshot(text = "Webpage not available", title = "", address = ADDRESS), isLoaded = true, hasFailed = true, isUnreachable = false) }

        assertEquals(UNREADABLE, source(browser).load(ADDRESS))
        assertTrue(browser.isClosed)
    }

    @Test
    fun `a load the network failed is unreachable rather than nothing to read`() = runTest {
        val browser = FakeBrowser { PageLook(snapshot = PageSnapshot(text = "No internet", title = "", address = ADDRESS), isLoaded = true, hasFailed = true, isUnreachable = true) }

        assertEquals(UNREACHABLE, source(browser).load(ADDRESS))
        assertTrue(browser.isClosed)
    }

    @Test
    fun `a page still loading with nothing drawn once the time is up is unreachable`() = runTest {
        val browser = FakeBrowser { PageLook(snapshot = PageSnapshot(text = "", title = "", address = ADDRESS), isLoaded = false, hasFailed = false, isUnreachable = false) }

        assertEquals(UNREACHABLE, source(browser).load(ADDRESS))
        assertEquals(OffscreenPageSource.TIME_LIMIT.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `a page that loaded with no text is nothing to read`() = runTest {
        val browser = FakeBrowser { PageLook(snapshot = PageSnapshot(text = " ", title = "Songs", address = ADDRESS), isLoaded = true, hasFailed = false, isUnreachable = false) }

        assertEquals(UNREADABLE, source(browser).load(ADDRESS))
    }

    @Test
    fun `a web view that throws is no page and is still let go of`() = runTest {
        val logged = mutableListOf<String>()
        val browser = FakeBrowser { error("The renderer is gone") }

        assertEquals(UNREADABLE, OffscreenPageSource(newBrowser = { browser }, logger = { logged += it }).load(ADDRESS))
        assertTrue(browser.isClosed)
        assertEquals(1, logged.size)
    }

    @Test
    fun `a web view that cannot be made is no page`() = runTest {
        val logged = mutableListOf<String>()

        assertEquals(UNREADABLE, OffscreenPageSource(newBrowser = { error("No WebView package") }, logger = { logged += it }).load(ADDRESS))
        assertEquals(1, logged.size)
    }

    private fun source(browser: FakeBrowser) = OffscreenPageSource(newBrowser = { browser }, logger = Logger { })

    private class FakeBrowser(private val look: () -> PageLook) : OffscreenBrowser {
        var openedAddress: String? = null
        val isOpened get() = openedAddress != null
        var isClosed = false

        override suspend fun open(address: String) {
            openedAddress = address
        }

        override suspend fun look() = look.invoke()

        override suspend fun close() {
            isClosed = true
        }
    }

    private companion object {
        val UNREACHABLE = LoadedPage.NotRead(LoadedPage.NotRead.Reason.UNREACHABLE)
        val UNREADABLE = LoadedPage.NotRead(LoadedPage.NotRead.Reason.UNREADABLE)
        const val ADDRESS = "https://chords.example.com/wonderwall"
        const val SONG = "[Verse 1]\nEm7       G\nToday is gonna be the day\n"
    }
}
