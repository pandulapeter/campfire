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

import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The fingerprint is stored in the registry and compared by every device with whatever version it runs, so a change to
 * it builds every shared copy again on every device. The values are the ones the 5.5.0 release computes.
 */
class SharedCopyFingerprintTest {

    @Test
    fun `a song shared alone without pictures keeps its fingerprint`() {
        assertEquals(
            "6febb976c500b31a5b1389ec17358470dc811b2e028bde257279d11279611bb6",
            sharedCopyFingerprint(kind = SharedLinkKind.SONG, fileName = SONG, hash = "1f2e3d", songs = null, images = emptyList()),
        )
    }

    @Test
    fun `a setlist naming a song the library does not have keeps its fingerprint`() {
        assertEquals(
            "8a27fbb1278157337658453f22ddd55eb55323ae4eb8f7677b017abd69127da9",
            sharedCopyFingerprint(
                kind = SharedLinkKind.SETLIST,
                fileName = "sunday.setlist.json",
                hash = "9a8b7c",
                songs = listOf(SONG to "1f2e3d", "missing-song.cho" to null),
                images = emptyList(),
            ),
        )
    }

    @Test
    fun `a song showing pictures keeps its fingerprint`() {
        assertEquals(
            "43d0cb2ac4a6cacef570915e625fc25afbdc43267f696bbb394eeca8d3145f02",
            sharedCopyFingerprint(
                kind = SharedLinkKind.SONG,
                fileName = SONG,
                hash = "1f2e3d",
                songs = null,
                images = listOf("0a1b2c.png", "3d4e5f.jpg"),
            ),
        )
    }

    private companion object {
        const val SONG = "amazing_grace-john_newton.cho"
    }
}
