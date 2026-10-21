/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.sync

import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.ShareState
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedItemsTest {

    private val shares = listOf(share(SharedLinkKind.SETLIST, "gig.setlist.json"), share(SharedLinkKind.SONG, "song.cho"))

    @Test
    fun `an item is shared under any case of its name, and only as its own kind`() {
        assertTrue(shares.isShared(SharedLinkKind.SETLIST, "Gig.setlist.json"))
        assertTrue(shares.isShared(SharedLinkKind.SONG, "song.cho"))
        assertFalse(shares.isShared(SharedLinkKind.SETLIST, "song.cho"))
        assertFalse(shares.isShared(SharedLinkKind.SONG, "other.cho"))
    }

    private fun share(kind: SharedLinkKind, fileName: String) = Share(
        shareId = fileName,
        kind = kind,
        fileName = fileName,
        title = fileName,
        link = assertNotNull(ShareLinks.create(kind, shareId = "aaaaaaaaaaaaaaaa", fileId = "file", rlkey = "key", title = fileName)),
        sharedAt = null,
        state = ShareState.OK,
    )
}
