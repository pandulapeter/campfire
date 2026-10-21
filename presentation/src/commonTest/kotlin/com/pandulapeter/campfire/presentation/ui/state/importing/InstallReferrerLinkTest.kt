/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.share.SharedLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class InstallReferrerLinkTest {

    @Test
    fun `the landing page's referrer is the link it names`() {
        val link = assertIs<SharedLink.Readable>(installReferrerLink("campfire_link=$PAYLOAD"))
        assertEquals("Campfire night", link.title)
        assertEquals("abcdefghijklmnop", link.shareId)
    }

    @Test
    fun `parameters beside the link change nothing`() {
        assertIs<SharedLink.Readable>(installReferrerLink("utm_source=campfire-songbook.com&campfire_link=$PAYLOAD&utm_medium=link"))
    }

    @Test
    fun `a newer version's link is answered as one`() {
        assertIs<SharedLink.FromNewerVersion>(installReferrerLink("campfire_link=2.something.new"))
    }

    @Test
    fun `Play's own, a campaign's, and an empty referrer are no link`() {
        assertNull(installReferrerLink("utm_source=google-play&utm_medium=organic"))
        assertNull(installReferrerLink("utm_source=newsletter&utm_campaign=autumn"))
        assertNull(installReferrerLink(""))
    }

    @Test
    fun `a payload the grammar does not read, or another parameter holding one, is no link`() {
        assertNull(installReferrerLink("campfire_link=1.setlist.short.fileid.rlkey.Title"))
        assertNull(installReferrerLink("campfire_link=https://example.com/s/#$PAYLOAD"))
        assertNull(installReferrerLink("link=$PAYLOAD"))
        assertNull(installReferrerLink("campfire_link="))
    }
}

private const val PAYLOAD = "1.setlist.abcdefghijklmnop.fileid.rlkey.Campfire%20night"
