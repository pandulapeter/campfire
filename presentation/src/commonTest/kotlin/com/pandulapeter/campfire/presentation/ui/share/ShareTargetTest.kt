/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.ShareState
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ShareTargetTest {

    private val shares = listOf(share(SharedLinkKind.SETLIST, "gig.setlist.json"), share(SharedLinkKind.SONG, "song.cho"))

    @Test
    fun `a target finds its share under any case of its name, and only as its own kind`() {
        assertEquals(shares[0], shareOf(shares, ShareTarget(SharedLinkKind.SETLIST, "Gig.setlist.json")))
        assertEquals(shares[1], shareOf(shares, ShareTarget(SharedLinkKind.SONG, "song.cho")))
        assertNull(shareOf(shares, ShareTarget(SharedLinkKind.SETLIST, "song.cho")))
        assertNull(shareOf(shares, ShareTarget(SharedLinkKind.SONG, "other.cho")))
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
