/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the sync index is filed under. A key that changes while the account stays the same makes the next run ignore
 * the index, and a run without one brings back every file that was deleted since the last.
 */
class SyncAccountKeyTest {

    @Test
    fun `the key is the id of the account`() {
        assertEquals("dropbox:dbid:1", account("dbid:1", "Jane", "jane@example.com").indexKey())
    }

    @Test
    fun `the key does not change when the name is read for the first time`() {
        assertEquals(account("dbid:1", "dbid:1", null).indexKey(), account("dbid:1", "Jane", "jane@example.com").indexKey())
    }

    @Test
    fun `the key does not change with the e-mail address`() {
        assertEquals(
            expected = account("dbid:1", "Jane", "jane@example.com").indexKey(),
            actual = account("dbid:1", "Jane", "jane@example.org").indexKey(),
        )
    }

    @Test
    fun `an account without an id is filed the way it used to be`() {
        assertEquals("dropbox:jane@example.com", account("", "Jane", "jane@example.com").indexKey())
        assertEquals("dropbox:Jane", account("", "Jane", null).indexKey())
    }

    @Test
    fun `an index filed under the e-mail address is adopted`() {
        val document = SyncIndexDocument(
            providerId = "dropbox",
            accountId = "dropbox:jane@example.com",
            lastSyncedAt = 42,
            entries = ENTRIES,
        )

        val adopted = document.adoptedBy(account("dbid:1", "Jane", "jane@example.com"))

        assertEquals("dropbox:dbid:1", adopted.accountId)
        assertEquals(ENTRIES, adopted.entries)
        assertEquals(42, adopted.lastSyncedAt)
    }

    @Test
    fun `an index filed under the id before the name was read matches as it is`() {
        val document = SyncIndexDocument(providerId = "dropbox", accountId = "dropbox:dbid:1", entries = ENTRIES)
        assertEquals(document, document.adoptedBy(account("dbid:1", "Jane", "jane@example.com")))
    }

    @Test
    fun `an index of another account is not adopted`() {
        val document = SyncIndexDocument(providerId = "dropbox", accountId = "dropbox:john@example.com", entries = ENTRIES)
        assertEquals(document, document.adoptedBy(account("dbid:1", "Jane", "jane@example.com")))
    }

    @Test
    fun `an index filed under a display name is not adopted`() {
        val document = SyncIndexDocument(providerId = "dropbox", accountId = "dropbox:Jane", entries = ENTRIES)
        assertEquals(document, document.adoptedBy(account("dbid:1", "Jane", "jane@example.com")))
    }

    @Test
    fun `an empty index is not adopted`() {
        val document = SyncIndexDocument()
        assertEquals(document, document.adoptedBy(account("dbid:1", "", null)))
    }

    private fun account(id: String, displayName: String, email: String?) = SyncAccount(
        providerId = SyncProviderId.DROPBOX,
        id = id,
        displayName = displayName,
        email = email,
    )

    private companion object {
        val ENTRIES = mapOf("songs/a.cho" to SyncIndexDocument.Entry(localHash = "h", remoteRevision = "r"))
    }
}
