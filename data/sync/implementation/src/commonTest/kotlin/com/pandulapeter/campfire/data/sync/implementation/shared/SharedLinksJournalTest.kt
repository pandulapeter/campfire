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

import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.sync.implementation.testEnvironment
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SharedLinksJournalTest {

    private val rename = SharedLinksJournal.Entry.Rename(from = "a.cho", to = "b.cho")
    private val stop = SharedLinksJournal.Entry.Stop(shareId = "aaaaaaaaaaaaaaaa")

    @Test
    fun `entries are kept in order across instances`() = runTest {
        val localSource = FakeSharedLinksLocalSource()
        SharedLinksJournal(localSource, testEnvironment()).run {
            append(listOf(rename))
            append(listOf(stop))
        }

        assertEquals(listOf(rename, stop), SharedLinksJournal(localSource, testEnvironment()).load())
    }

    @Test
    fun `stops by name and of everything are kept across instances`() = runTest {
        val localSource = FakeSharedLinksLocalSource()
        val entries = listOf(SharedLinksJournal.Entry.StopItem(kind = "song", fileName = "a.cho"), SharedLinksJournal.Entry.StopAll)
        SharedLinksJournal(localSource, testEnvironment()).append(entries)

        assertEquals(entries, SharedLinksJournal(localSource, testEnvironment()).load())
    }

    @Test
    fun `a journal written before stops by name still reads`() = runTest {
        val localSource = FakeSharedLinksLocalSource(
            journal = """{"entries": [{"type": "rename", "from": "a.cho", "to": "b.cho"}, {"type": "stop", "shareId": "aaaaaaaaaaaaaaaa"}]}""",
        )

        assertEquals(listOf(rename, stop), SharedLinksJournal(localSource, testEnvironment()).load())
    }

    @Test
    fun `removing the applied entries keeps one appended meanwhile, and the last removal deletes the file`() = runTest {
        val localSource = FakeSharedLinksLocalSource()
        val journal = SharedLinksJournal(localSource, testEnvironment())
        journal.append(listOf(rename))
        val applied = journal.load()
        journal.append(listOf(stop))

        journal.remove(applied)
        assertEquals(listOf(stop), journal.load())
        journal.remove(listOf(stop))
        assertNull(localSource.journal)
    }

    @Test
    fun `a journal that cannot be read is never written over`() = runTest {
        val localSource = FakeSharedLinksLocalSource(journal = "kept", onLoadJournal = { throw LibraryStorageException("busy") })

        assertFailsWith<LibraryStorageException> { SharedLinksJournal(localSource, testEnvironment()).append(listOf(stop)) }
        assertEquals("kept", localSource.journal)
    }
}
