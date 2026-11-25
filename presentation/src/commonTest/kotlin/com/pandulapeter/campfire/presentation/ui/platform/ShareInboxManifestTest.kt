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

import com.pandulapeter.campfire.data.model.importing.PageLineMarks
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
    fun `a file taken out of a share leaves a manifest of the rest that reads back the same`() {
        val manifest = ShareInboxManifest(version = 1, subject = "Songs", files = listOf("files/0/library.zip", "files/1/song.cho"))

        val rest = assertNotNull(manifest.withoutFile("files/0/library.zip"))

        assertEquals(manifest.copy(files = listOf("files/1/song.cho")), rest)
        assertEquals(rest, decodeShareInboxManifest(encodeShareInboxManifest(rest)))
    }

    @Test
    fun `a share whose last file was taken is nothing`() {
        assertNull(ShareInboxManifest(version = 1, files = listOf("files/0/library.zip")).withoutFile("files/0/library.zip"))
        assertNotNull(ShareInboxManifest(version = 1, files = listOf("files/0/a.zip"), texts = listOf("texts/0.txt")).withoutFile("files/0/a.zip"))
    }

    @Test
    fun `a page's marks are decoded with a blank or unaligned line's as none and none without them`() {
        val manifest = assertNotNull(
            decodeShareInboxManifest(
                """{"version": 1, "texts": ["texts/0.txt"], "marks": [{"link": 1, "newBlock": 1, "landmark": 0, "size": 0.9, "bold": 1,
                "italic": 0, "monospace": 0, "heading": 0}, {}, {"unaligned": 1, "covered": 0.5}]}""",
            ),
        )

        assertEquals(
            listOf(
                PageLineMarks(size = 0.9f, isBold = true, isItalic = false, isMonospace = false, isHeading = false, isLink = true, isNewBlock = true, isLandmark = false),
                null,
                null,
            ),
            manifest.pageMarks(),
        )
        assertNull(assertNotNull(decodeShareInboxManifest("""{"version": 1, "texts": ["texts/0.txt"]}""")).pageMarks())
    }

    @Test
    fun `marks the page broke leave the share read without them`() {
        val manifest = assertNotNull(decodeShareInboxManifest("""{"version": 1, "texts": ["texts/0.txt"], "marks": [{"size": "big"}]}"""))

        assertEquals(listOf("texts/0.txt"), manifest.texts)
        assertNull(manifest.pageMarks())
    }

    @Test
    fun `anything that is not a manifest is null`() {
        listOf("", "garbage", "[]", """{"subject": "No version"}""", """{"version": "one"}""").forEach { assertNull(decodeShareInboxManifest(it)) }
    }
}
