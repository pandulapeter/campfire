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

class SharedCopyChangesTest {

    @Test
    fun `a rename that knows its share renames only that one`() {
        val changes = SharedCopyChanges().apply { rename(from = "a-b.cho", to = "a-c.cho", shareId = "s2") }
        val document = document(entry("s1", "a-b.cho"), entry("s2", "old_name.cho"))
        assertEquals(listOf("a-b.cho", "a-c.cho"), changes.applyTo(document).links.map { it.fileName })
    }

    @Test
    fun `a rename without a share renames every entry of the name in any case`() {
        val changes = SharedCopyChanges().apply { rename(from = "a-b.cho", to = "a-c.cho", shareId = null) }
        val document = document(entry("s1", "A-B.cho"), entry("s2", "a-b.cho", SharedLinkKind.SETLIST), entry("s3", "x-y.cho"))
        assertEquals(listOf("a-c.cho", "a-c.cho", "x-y.cho"), changes.applyTo(document).links.map { it.fileName })
    }

    @Test
    fun `renames apply in the order they were taken`() {
        val changes = SharedCopyChanges().apply {
            rename(from = "a.cho", to = "b.cho", shareId = null)
            rename(from = "b.cho", to = "c.cho", shareId = null)
        }
        assertEquals(listOf("c.cho"), changes.applyTo(document(entry("s1", "a.cho"))).links.map { it.fileName })
    }

    @Test
    fun `a deleted item is found under the name an earlier rename gave it`() {
        val changes = SharedCopyChanges()
        val document = document(entry("s1", "a.cho"), entry("s2", "b.cho"))
        changes.take(SharedLinksJournal.Entry.Rename(from = "a.cho", to = "c.cho"), document)
        assertEquals(listOf(), changes.stoppedBy(SharedLinksJournal.Entry.StopItem(kind = SONG, fileName = "a.cho"), document))
        changes.take(SharedLinksJournal.Entry.StopItem(kind = SONG, fileName = "C.cho"), document)
        assertEquals(setOf("s1"), changes.removed)
        assertEquals(listOf("s2"), changes.applyTo(document).links.map { it.shareId })
    }

    @Test
    fun `a deleted item of another kind or of an unknown kind stops nothing`() {
        val changes = SharedCopyChanges()
        val document = document(entry("s1", "a.cho"))
        assertEquals(listOf(), changes.stoppedBy(SharedLinksJournal.Entry.StopItem(kind = SharedLinkKind.SETLIST.id, fileName = "a.cho"), document))
        assertEquals(listOf(), changes.stoppedBy(SharedLinksJournal.Entry.StopItem(kind = "album", fileName = "a.cho"), document))
    }

    @Test
    fun `a stop names its share and a stop of everything every share the renamed list holds`() {
        val changes = SharedCopyChanges().apply { remove("s1") }
        val document = document(entry("s1", "a.cho"), entry("s2", "b.cho"), entry("s3", "c.cho"))
        assertEquals(listOf("s9"), changes.stoppedBy(SharedLinksJournal.Entry.Stop(shareId = "s9"), document))
        assertEquals(listOf("s2", "s3"), changes.stoppedBy(SharedLinksJournal.Entry.StopAll, document))
        assertEquals(listOf(), changes.stoppedBy(SharedLinksJournal.Entry.Rename(from = "a.cho", to = "b.cho"), document))
    }

    @Test
    fun `a removed share is not updated and updates see the renamed entry`() {
        val changes = SharedCopyChanges().apply {
            update("s1") { it.copy(title = "Gone") }
            update("s2") { it.copy(title = it.fileName) }
            remove("s1")
            rename(from = "b.cho", to = "d.cho", shareId = "s2")
        }
        val written = changes.applyTo(document(entry("s1", "a.cho"), entry("s2", "b.cho")))
        assertEquals(listOf("s2" to "d.cho"), written.links.map { it.shareId to it.title })
    }

    @Test
    fun `updates of one share compose in the order they were asked`() {
        val changes = SharedCopyChanges().apply {
            update("s1") { it.copy(title = it.title + "1") }
            update("s1") { it.copy(title = it.title + "2") }
        }
        assertEquals(listOf("T12"), changes.applyTo(document(entry("s1", "a.cho", title = "T"))).links.map { it.title })
    }

    @Test
    fun `changes naming a share that is gone leave the list as it is and apply twice the same`() {
        val changes = SharedCopyChanges().apply {
            rename(from = "x.cho", to = "y.cho", shareId = "s9")
            remove("s8")
            update("s7") { it.copy(title = "Changed") }
        }
        val document = document(entry("s1", "a.cho", title = "T"))
        assertEquals(document, changes.applyTo(document))
        val renaming = SharedCopyChanges().apply {
            rename(from = "a.cho", to = "b.cho", shareId = null)
            update("s1") { it.copy(title = "U") }
        }
        assertEquals(renaming.applyTo(document), renaming.applyTo(renaming.applyTo(document)))
    }

    private fun document(vararg entries: SharedLinksEntry) = SharedLinksDocument(links = entries.toList())

    private fun entry(shareId: String, fileName: String, kind: SharedLinkKind = SharedLinkKind.SONG, title: String = "") =
        SharedLinksEntry(shareId = shareId, kind = kind.id, fileName = fileName, title = title)

    private companion object {
        val SONG = SharedLinkKind.SONG.id
    }
}
