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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeWebViewEventsTest {

    @Test
    fun `only a public https page is opened`() {
        val events = NativeWebViewEvents()

        assertTrue(events.allowsNavigation("https://chords.example.com/song", isMainFrame = true))
        assertFalse(events.allowsNavigation("http://chords.example.com/song", isMainFrame = true))
        assertFalse(events.allowsNavigation("https://192.168.1.1/", isMainFrame = true))
        assertFalse(events.allowsNavigation("about:blank", isMainFrame = true))
    }

    @Test
    fun `a frame may also be one that goes nowhere`() {
        val events = NativeWebViewEvents()

        assertTrue(events.allowsNavigation("about:blank", isMainFrame = false))
        assertTrue(events.allowsNavigation("data:text/html,hi", isMainFrame = false))
        assertFalse(events.allowsNavigation("file:///etc/passwd", isMainFrame = false))
        assertFalse(events.allowsNavigation("https://localhost/", isMainFrame = false))
    }

    @Test
    fun `no picture is fetched`() {
        val events = NativeWebViewEvents()

        assertFalse(events.allowsResource("https://chords.example.com/cover.jpg", isImage = true))
        assertTrue(events.allowsResource("https://chords.example.com/app.js", isImage = false))
        assertFalse(events.allowsResource("http://chords.example.com/app.js", isImage = false))
    }

    @Test
    fun `a failure the network caused is told apart from the page's`() {
        val unreachable = NativeWebViewEvents().apply { onLoadFailed(NativeLoadFailure.WEB_VIEW2, 13) }
        val failed = NativeWebViewEvents().apply { onLoadFailed(NativeLoadFailure.WEB_VIEW2, 5) }
        val cancelled = NativeWebViewEvents().apply { onLoadFailed(NativeLoadFailure.WEB_VIEW2, 14) }

        assertTrue(unreachable.hasFailed && unreachable.isUnreachable)
        assertTrue(failed.hasFailed && !failed.isUnreachable)
        assertFalse(cancelled.hasFailed)
    }

    @Test
    fun `a script's answer reaches the look waiting for it`() = runTest {
        val events = NativeWebViewEvents()
        val answer = events.expectScriptResult()

        events.onScriptResult("{}")

        assertEquals("{}", answer.await())
    }
}
