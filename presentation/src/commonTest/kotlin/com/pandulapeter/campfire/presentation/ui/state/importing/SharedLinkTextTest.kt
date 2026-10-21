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
import kotlin.test.assertIs
import kotlin.test.assertNull

class SharedLinkTextTest {

    @Test
    fun `a text that is a link in any of its forms is that link`() {
        assertIs<SharedLink.Readable>(sharedLinkIn("https://campfire-songbook.com/s/#$PAYLOAD"))
        assertIs<SharedLink.Readable>(sharedLinkIn("  campfire://s/$PAYLOAD\n"))
        assertIs<SharedLink.Readable>(sharedLinkIn(PAYLOAD))
    }

    @Test
    fun `a newer version's link is one only in a link's address`() {
        assertIs<SharedLink.FromNewerVersion>(sharedLinkIn("https://campfire-songbook.com/s/#2.something.new"))
        assertIs<SharedLink.FromNewerVersion>(sharedLinkIn("campfire://s/2.something.new"))
        assertNull(sharedLinkIn("2.5"))
        assertNull(sharedLinkIn("3.14"))
    }

    @Test
    fun `a link beside other words, another address, or an ordinary text is no link`() {
        assertNull(sharedLinkIn("Look: https://campfire-songbook.com/s/#$PAYLOAD"))
        assertNull(sharedLinkIn("https://example.com/s/#$PAYLOAD"))
        assertNull(sharedLinkIn("Am  C\nHello"))
        assertNull(sharedLinkIn(""))
    }
}

private const val PAYLOAD = "1.setlist.abcdefghijklmnop.fileid.rlkey.Campfire%20night"
