/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ShareIdsTest {

    @Test
    fun `a new share id makes a link and is never the same twice`() {
        val ids = List(1000) { ShareIds.generate() }

        ids.forEach { assertNotNull(ShareLinks.create(kind = SharedLinkKind.SONG, shareId = it, fileId = "f", rlkey = "k", title = "T")) }
        assertEquals(ids.size, ids.toSet().size)
    }
}
