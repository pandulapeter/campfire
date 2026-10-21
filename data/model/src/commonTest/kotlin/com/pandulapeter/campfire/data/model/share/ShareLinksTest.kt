/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ShareLinksTest {

    private val link = assertNotNull(
        ShareLinks.create(
            kind = SharedLinkKind.SETLIST,
            shareId = "k3jq7abcdz2m5x6y",
            fileId = "abc123def456ghi789jkl",
            rlkey = "a1b2c3d4e5f6g7h8i9j0k1l2m",
            title = "Summer gig",
        ),
    )

    @Test
    fun `a link reads back from the landing page`() {
        assertEquals(link, ShareLinks.parse(link.landingUrl))
    }

    @Test
    fun `the landing page carries the payload in its fragment`() {
        assertEquals(
            "https://campfire-songbook.com/s/#1.setlist.k3jq7abcdz2m5x6y.abc123def456ghi789jkl.a1b2c3d4e5f6g7h8i9j0k1l2m.Summer%20gig",
            link.landingUrl,
        )
    }

    @Test
    fun `a link reads back from the web app, the scheme, and the bare payload`() {
        assertEquals(link, ShareLinks.parse("https://campfire-songbook.com/app/#${link.payload}"))
        assertEquals(link, ShareLinks.parse("campfire://s/${link.payload}"))
        assertEquals(link, ShareLinks.parse(link.payload))
    }

    @Test
    fun `a messenger's query before the fragment is ignored`() {
        assertEquals(link, ShareLinks.parse("https://campfire-songbook.com/s/?fbclid=IwAR0abc#${link.payload}"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(link, ShareLinks.parse("  ${link.landingUrl}\n"))
    }

    @Test
    fun `the download is built from the parts on Dropbox's direct host`() {
        assertEquals(
            "https://dl.dropboxusercontent.com/scl/fi/abc123def456ghi789jkl/k3jq7abcdz2m5x6y.zip?rlkey=a1b2c3d4e5f6g7h8i9j0k1l2m&dl=1",
            link.downloadUrl,
        )
    }

    @Test
    fun `a song link keeps its kind`() {
        val song = assertNotNull(ShareLinks.create(SharedLinkKind.SONG, "k3jq7abcdz2m5x6y", "f", "r", "Wonderwall"))
        assertEquals(SharedLinkKind.SONG, (ShareLinks.parse(song.landingUrl) as SharedLink.Readable).kind)
    }

    @Test
    fun `a title with every kind of character survives the round trip`() {
        listOf(
            "Árvíztűrő tükörfúrógép",
            "Don't stop me now (Live)",
            "A.B.C. 1.2.3",
            "100% sure & 50/50? #1 \"quoted\" <b>bold</b>",
            "Ünnep — 🔥 campfire 🎸",
            "夜に駆ける",
            "Песня о друге",
            "مرحبا",
            "",
        ).forEach { title ->
            val created = assertNotNull(ShareLinks.create(SharedLinkKind.SONG, "k3jq7abcdz2m5x6y", "f", "r", title))
            assertEquals(title, (ShareLinks.parse(created.landingUrl) as SharedLink.Readable).title, title)
            assertEquals(6, created.payload.split('.').size, title)
        }
    }

    @Test
    fun `a long title is cut on a character boundary`() {
        val title = "é".repeat(100)
        val created = assertNotNull(ShareLinks.create(SharedLinkKind.SETLIST, "k3jq7abcdz2m5x6y", "f", "r", title))
        assertEquals("é".repeat(60), created.title)
        val emoji = "a" + "🔥".repeat(40)
        val cut = assertNotNull(ShareLinks.create(SharedLinkKind.SETLIST, "k3jq7abcdz2m5x6y", "f", "r", emoji))
        assertEquals("a" + "🔥".repeat(29), cut.title)
    }

    @Test
    fun `control characters in a title become spaces, and format characters other than the joiners go`() {
        fun titleOf(encoded: String) = (ShareLinks.parse("1.song.k3jq7abcdz2m5x6y.f.r.$encoded") as SharedLink.Readable).title
        assertEquals("one two", titleOf("one%0Atwo"))
        assertEquals("tab here", titleOf("tab%09here"))
        assertEquals("evil", titleOf("%E2%80%AEevil"))
        assertEquals("a b", titleOf("a%E2%80%8B%20b"))
        assertEquals("👨\u200D👩\u200D👧", titleOf("%F0%9F%91%A8%E2%80%8D%F0%9F%91%A9%E2%80%8D%F0%9F%91%A7"))
        assertEquals("tab here", assertNotNull(ShareLinks.create(SharedLinkKind.SONG, "k3jq7abcdz2m5x6y", "f", "r", "tab\there")).title)
    }

    @Test
    fun `lowercase escapes are read too`() {
        val parsed = ShareLinks.parse("1.song.k3jq7abcdz2m5x6y.f.r.caf%c3%a9") as SharedLink.Readable
        assertEquals("café", parsed.title)
    }

    @Test
    fun `a later version is told apart from a broken link`() {
        assertEquals(SharedLink.FromNewerVersion(2), ShareLinks.parse("https://campfire-songbook.com/s/#2.anything.new-here"))
        assertEquals(SharedLink.FromNewerVersion(15), ShareLinks.parse("campfire://s/15.x"))
    }

    @Test
    fun `parts the Dropbox link does not fit the grammar with make no link`() {
        assertNull(ShareLinks.create(SharedLinkKind.SONG, "k3jq7abcdz2m5x6y", "a".repeat(65), "r", "t"))
        assertNull(ShareLinks.create(SharedLinkKind.SONG, "k3jq7abcdz2m5x6y", "f", "r/../x", "t"))
        assertNull(ShareLinks.create(SharedLinkKind.SONG, "short", "f", "r", "t"))
    }

    @Test
    fun `anything else is not a link`() {
        val payload = link.payload
        listOf(
            "",
            "   ",
            "https://campfire-songbook.com/s/#",
            "campfire://s/",
            "https://example.com/s/#$payload",
            "http://campfire-songbook.com/s/#$payload",
            "https://campfire-songbook.com.example/s/#$payload",
            "https://evil.campfire-songbook.com/s/#$payload",
            "https://campfire-songbook.com:443/s/#$payload",
            "https://campfire-songbook.com/x/#$payload",
            "https://campfire-songbook.com/s/$payload",
            "https://campfire-songbook.com/s/#$payload#",
            "https://campfire-songbook.com/s/#$payload extra words",
            "campfire://oauth?code=abc123",
            "campfire://s/#$payload",
            "campfire://x/$payload",
            "0.setlist.k3jq7abcdz2m5x6y.f.r.t",
            "01.setlist.k3jq7abcdz2m5x6y.f.r.t",
            "v1.setlist.k3jq7abcdz2m5x6y.f.r.t",
            "1.playlist.k3jq7abcdz2m5x6y.f.r.t",
            "1.setlist.k3jq7abcdz2m5x6y.f.r",
            "1.setlist.k3jq7abcdz2m5x6y.f.r.t.extra",
            "1.setlist.k3jq7abcdz2m5x6y..r.t",
            "1.setlist.k3jq7abcdz2m5x6y.f..t",
            "1.setlist.K3JQ7ABCDZ2M5X6Y.f.r.t",
            "1.setlist.k3jq7abcdz2m5x61.f.r.t",
            "1.setlist.k3jq7abcdz2m5x6.f.r.t",
            "1.setlist.k3jq7abcdz2m5x6yy.f.r.t",
            "1.setlist.k3jq7abcdz2m5x6y.f+.r.t",
            "1.setlist.k3jq7abcdz2m5x6y.f.r%2F.t",
            "1.setlist.k3jq7abcdz2m5x6y.${"f".repeat(65)}.r.t",
            "1.setlist.k3jq7abcdz2m5x6y.f.${"r".repeat(65)}.t",
            "1.setlist.k3jq7abcdz2m5x6y.f.r.a b",
            "1.setlist.k3jq7abcdz2m5x6y.f.r.a%2",
            "1.setlist.k3jq7abcdz2m5x6y.f.r.a%zz",
            "1.setlist.k3jq7abcdz2m5x6y.f.r.${"a".repeat(121)}",
            "1.setlist.k3jq7abcdz2m5x6y.f.r.%C3",
            "1.setlist.k3jq7abcdz2m5x6y.f.r.%FF%FE",
            "2",
            "2.",
            "2.a b",
            "Summer gig",
            "https://campfire-songbook.com/s/#$payload".repeat(100),
        ).forEach { address ->
            assertNull(ShareLinks.parse(address), address)
        }
    }
}
