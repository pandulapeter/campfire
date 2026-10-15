/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareInboxManifestTest {

    @Test
    fun `a manifest is decoded`() {
        val manifest = assertNotNull(
            decodeShareInboxManifest("""{"version": 1, "subject": "Song", "files": ["files/0/Song.pdf"], "texts": ["texts/0.txt"]}"""),
        )
        assertEquals(ShareInboxManifest(version = 1, subject = "Song", files = listOf("files/0/Song.pdf"), texts = listOf("texts/0.txt")), manifest)
        assertFalse(manifest.isFromNewerVersion)
    }

    @Test
    fun `missing optional fields are empty`() {
        assertEquals(ShareInboxManifest(version = 1), decodeShareInboxManifest("""{"version": 1}"""))
    }

    @Test
    fun `an unknown field is ignored`() {
        assertEquals(ShareInboxManifest(version = 1, texts = listOf("texts/0.txt")), decodeShareInboxManifest("""{"version": 1, "texts": ["texts/0.txt"], "source": "Safari"}"""))
    }

    @Test
    fun `a manifest of a newer version says so`() {
        assertTrue(assertNotNull(decodeShareInboxManifest("""{"version": 2, "items": []}""")).isFromNewerVersion)
    }

    @Test
    fun `anything that is not a manifest is null`() {
        listOf("", "garbage", "[]", """{"subject": "No version"}""", """{"version": "one"}""").forEach { assertNull(decodeShareInboxManifest(it)) }
    }
}
