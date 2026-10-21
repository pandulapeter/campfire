/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.settings_sharing
import com.pandulapeter.campfire.presentation.resources.settings_sharing_actions
import com.pandulapeter.campfire.presentation.resources.settings_sharing_description
import com.pandulapeter.campfire.presentation.resources.settings_sharing_nothing_shared
import com.pandulapeter.campfire.presentation.resources.settings_sharing_orphaned
import com.pandulapeter.campfire.presentation.resources.settings_sharing_show_qr_code
import com.pandulapeter.campfire.presentation.resources.settings_sharing_stop_all
import com.pandulapeter.campfire.presentation.resources.settings_sync_connect_dropbox
import com.pandulapeter.campfire.presentation.resources.share_copy
import com.pandulapeter.campfire.presentation.resources.share_qr_code
import com.pandulapeter.campfire.presentation.resources.songs_export_song
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.RISING_SUN_TITLE
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class SharingTabTest {

    @Test
    fun `without an account the sharing tab invites to connect and says the library syncs too`() = runAppTest(
        window = WIDE_WINDOW,
        isSyncConnected = false,
    ) {
        openSharingTab()

        onNodeWithText(string(Res.string.settings_sharing_description)).assertExists()
        onNode(hasText(string(Res.string.settings_sync_connect_dropbox)) and hasClickAction()).assertExists()
        onNodeWithText(string(Res.string.settings_sharing_stop_all)).assertDoesNotExist()
    }

    @Test
    fun `the sharing tab lists what this device last read of the account's shares`() = runAppTest(
        window = WIDE_WINDOW,
        preferenceFiles = mapOf("shared-links.json" to MIRROR),
    ) {
        openSharingTab()

        onNodeWithText(RISING_SUN_TITLE).assertExists()
        onNodeWithText(GONE_SETLIST_TITLE).assertExists()
        onNodeWithText(string(Res.string.settings_sharing_orphaned)).assertExists()
        onNodeWithText(string(Res.string.settings_sharing_nothing_shared)).assertDoesNotExist()
        onNode(hasText(string(Res.string.settings_sharing_stop_all)) and hasClickAction()).assertExists()
    }

    @Test
    fun `a shared song's show qr code opens its export screen on the link`() = runAppTest(
        window = WIDE_WINDOW,
        preferenceFiles = mapOf("shared-links.json" to MIRROR),
    ) {
        openSharingTab()

        // The most recently shared first: the song, whose item the library holds.
        onAllNodes(hasContentDescription(string(Res.string.settings_sharing_actions)))[0].performTouchInput { click() }
        waitForIdle()
        onNode(hasText(string(Res.string.share_copy)) and hasClickAction()).assertExists()
        tap(hasText(string(Res.string.settings_sharing_show_qr_code)) and hasClickAction())

        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasContentDescription(string(Res.string.share_qr_code))).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a phone on its side shows the qr code whole, under the app bar and clear of copy link`() = runAppTest(
        window = TestWindow.PHONE_ON_ITS_SIDE,
        preferenceFiles = mapOf("shared-links.json" to MIRROR),
    ) {
        openQrCode()
        waitForIdle()

        // Clipped to the scrolling column's viewport, so a code cut at its edge has bounds smaller than its size.
        val code = onNode(hasContentDescription(string(Res.string.share_qr_code))).fetchSemanticsNode()
        val shown = code.boundsInRoot
        assertTrue(code.size.width > 0)
        assertEquals(code.size.width.toFloat(), shown.width, absoluteTolerance = 1f)
        assertEquals(code.size.height.toFloat(), shown.height, absoluteTolerance = 1f)
        val title = onNodeWithText(string(Res.string.songs_export_song)).fetchSemanticsNode().boundsInRoot
        assertTrue(shown.top >= title.bottom)
        val copyLink = onNode(hasContentDescription(string(Res.string.share_copy))).fetchSemanticsNode().boundsInRoot
        assertFalse(shown.overlaps(copyLink))
    }

    @Test
    fun `a back over the qr code closes the export screen and leaves the sharing tab as it was`() = runAppTest(
        window = WIDE_WINDOW,
        preferenceFiles = mapOf("shared-links.json" to MIRROR),
    ) {
        openQrCode()

        pressBack()

        assertSharingTabUncovered()
    }

    @Test
    fun `a back gesture over the qr code closes the export screen and leaves the sharing tab as it was`() = runAppTest(
        window = WIDE_WINDOW,
        preferenceFiles = mapOf("shared-links.json" to MIRROR),
    ) {
        openQrCode()

        swipeBack()

        assertSharingTabUncovered()
    }

    private fun AppTestScope.openQrCode() {
        openSharingTab()
        onAllNodes(hasContentDescription(string(Res.string.settings_sharing_actions)))[0].performTouchInput { click() }
        waitForIdle()
        tap(hasText(string(Res.string.settings_sharing_show_qr_code)) and hasClickAction())
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasContentDescription(string(Res.string.share_qr_code))).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun AppTestScope.assertSharingTabUncovered() {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasContentDescription(string(Res.string.share_qr_code))).fetchSemanticsNodes().isEmpty() }
        onNode(hasText(string(Res.string.settings_sharing_stop_all)) and hasClickAction()).assertExists()
    }

    private fun AppTestScope.openSharingTab() {
        tap(hasText(string(Res.string.settings)) and hasClickAction())
        tap(hasText(string(Res.string.settings_sharing)) and hasClickAction())
    }

    private companion object {
        /** Wide enough for the settings screen's category pane, which shows only the page picked. */
        val WIDE_WINDOW = TestWindow(width = 1000.dp, height = 800.dp)

        const val GONE_SETLIST_TITLE = "A setlist deleted elsewhere"

        /** The shared links' mirror: a song the library holds, and a setlist it no longer does. */
        val MIRROR = """
            {
              "revision": "r1",
              "registry": {
                "version": 1,
                "links": [
                  {
                    "shareId": "aaaaaaaaaaaaaaaa", "kind": "song", "fileName": "$HOUSE_OF_THE_RISING_SUN",
                    "title": "$RISING_SUN_TITLE", "fileId": "file1", "rlkey": "key1", "sharedAt": "2026-10-20", "state": "ok"
                  },
                  {
                    "shareId": "bbbbbbbbbbbbbbbb", "kind": "setlist", "fileName": "gone.setlist.json",
                    "title": "$GONE_SETLIST_TITLE", "fileId": "file2", "rlkey": "key2", "sharedAt": "2026-10-01", "state": "orphaned"
                  }
                ]
              }
            }
        """.trimIndent()
    }
}
