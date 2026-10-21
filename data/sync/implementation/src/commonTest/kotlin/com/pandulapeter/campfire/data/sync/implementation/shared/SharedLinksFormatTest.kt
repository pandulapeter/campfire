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

import com.pandulapeter.campfire.data.model.share.ShareState
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SharedLinksFormatTest {

    @Test
    fun `an edit keeps the members a later version added, at the top and in each entry`() {
        val text = """
            { "version": 1, "owner": "later", "links": [ {
                "shareId": "aaaaaaaaaaaaaaaa", "kind": "song", "fileName": "a.cho", "title": "A", "fileId": "f",
                "rlkey": "k", "sharedAt": "2026-10-16", "state": "ok", "colour": "red"
            } ] }
        """.trimIndent()
        val document = readable(text)

        val edited = Json.parseToJsonElement(SharedLinksFormat.encode(document.copy(links = document.links.map { it.copy(title = "B") })))

        assertEquals(JsonPrimitive("later"), edited.jsonObject["owner"])
        val entry = edited.jsonObject.getValue("links").jsonArray.single().jsonObject
        assertEquals(JsonPrimitive("red"), entry["colour"])
        assertEquals(JsonPrimitive("B"), entry["title"])
    }

    @Test
    fun `what is written reads back the same`() {
        val document = SharedLinksDocument(
            links = listOf(
                SharedLinksEntry(
                    shareId = "aaaaaaaaaaaaaaaa",
                    kind = "setlist",
                    fileName = "gig.setlist.json",
                    title = "Gig",
                    fileId = "f",
                    rlkey = "k",
                    state = SharedLinksEntry.STATE_TOO_LARGE,
                    built = SharedCopyBuild(inputs = "i", build = 412),
                ),
            ),
        )

        assertEquals(document, readable(SharedLinksFormat.encode(document)))
    }

    @Test
    fun `a copy marked missing reads back as such, beside a member a later version added`() {
        val text = """
            { "links": [ {
                "shareId": "aaaaaaaaaaaaaaaa", "kind": "song", "fileName": "a.cho", "title": "A", "fileId": "f",
                "rlkey": "k", "state": "orphaned", "copyMissing": true, "colour": "red"
            } ] }
        """.trimIndent()

        val entry = readable(SharedLinksFormat.encode(readable(text))).links.single()

        assertTrue(entry.copyMissing)
        assertEquals(mapOf("colour" to JsonPrimitive("red")), entry.unknownFields)
        assertEquals(ShareState.COPY_MISSING, entry.toShare()?.state)
    }

    @Test
    fun `an entry written before copies were marked missing has its copy`() {
        val entry = readable("""{"links": [ {"shareId": "aaaaaaaaaaaaaaaa", "state": "orphaned"} ]}""").links.single()

        assertFalse(entry.copyMissing)
    }

    @Test
    fun `a later version's list is told apart from one that cannot be read`() {
        assertEquals(SharedLinksFormat.Decoded.FromNewerVersion(2), SharedLinksFormat.decode("""{"version": 2}"""))
        assertEquals(SharedLinksFormat.Decoded.Unreadable, SharedLinksFormat.decode("""{"version": "one"}"""))
        assertEquals(SharedLinksFormat.Decoded.Unreadable, SharedLinksFormat.decode("""[1, 2]"""))
        assertEquals(SharedLinksFormat.Decoded.Unreadable, SharedLinksFormat.decode("""{"links": {}}"""))
    }

    @Test
    fun `an entry without a share id is dropped and the rest are read`() {
        val document = readable("""{"links": [ {"title": "No id"}, 5, {"shareId": "aaaaaaaaaaaaaaaa"} ]}""")

        assertEquals(listOf("aaaaaaaaaaaaaaaa"), document.links.map { it.shareId })
    }

    @Test
    fun `the shares are the readable entries, most recently shared first`() {
        fun entry(shareId: String, sharedAt: String?, state: String = SharedLinksEntry.STATE_OK) = SharedLinksEntry(
            shareId = shareId,
            kind = "song",
            fileName = "$shareId.cho",
            title = shareId,
            fileId = "f",
            rlkey = "k",
            sharedAt = sharedAt,
            state = state,
        )
        val document = SharedLinksDocument(
            links = listOf(
                entry("aaaaaaaaaaaaaaaa", "2026-10-01"),
                entry("bbbbbbbbbbbbbbbb", null),
                entry("cccccccccccccccc", "2026-10-02", SharedLinksEntry.STATE_ORPHANED),
                entry("dddddddddddddddd", "2026-10-01", "unknown"),
                entry("not a share id!!", "2026-10-03"),
            ),
        )

        val shares = document.toShares()

        assertEquals(listOf("cccccccccccccccc", "dddddddddddddddd", "aaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb"), shares.map { it.shareId })
        assertEquals(listOf(ShareState.ORPHANED, ShareState.OK, ShareState.OK, ShareState.OK), shares.map { it.state })
        assertEquals(LocalDate(2026, 10, 2), shares.first().sharedAt)
    }

    private fun readable(text: String) = assertIs<SharedLinksFormat.Decoded.Readable>(SharedLinksFormat.decode(text)).document
}
