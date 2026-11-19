/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

import com.pandulapeter.campfire.data.sync.implementation.LocalFileState
import com.pandulapeter.campfire.data.sync.implementation.RemoteFileState
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.SyncOperation
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.BLOBS
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.checklist
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A reference beats a deletion for any app's documents: the content files another app's notes name, as its own
 * [SyncReferences] reads them, are kept on both sides exactly as a song's pictures are.
 */
class SyncPlannerReferencesTest {

    @Test
    fun `a content file a note here names goes back up when the folder lost it`() = assertEquals(
        expected = listOf<SyncOperation>(SyncOperation.Upload(BLOB, expectedRevision = null)),
        actual = plan(
            local = listOf(LocalFileState(NOTE, "a"), LocalFileState(BLOB, localImageHash(10))),
            remote = listOf(RemoteFileState(NOTE, "r1", contentHash = null)),
            index = mapOf(NOTE to SyncIndexEntry("a", "r1"), BLOB to SyncIndexEntry(localImageHash(10), "r1")),
            notes = mapOf(NOTE to NAMING),
        ).operations,
    )

    @Test
    fun `a content file a note here names comes back down when this device lost it`() = assertEquals(
        expected = listOf<SyncOperation>(SyncOperation.Download(BLOB, "r1")),
        actual = plan(
            local = listOf(LocalFileState(NOTE, "a")),
            remote = listOf(RemoteFileState(NOTE, "r1", contentHash = null), RemoteFileState(BLOB, "r1", contentHash = null)),
            index = mapOf(NOTE to SyncIndexEntry("a", "r1"), BLOB to SyncIndexEntry(localImageHash(10), "r1")),
            notes = mapOf(NOTE to NAMING),
        ).operations,
    )

    @Test
    fun `a content file no note names is deleted here once the folder lost it`() = assertEquals(
        expected = listOf<SyncOperation>(SyncOperation.DeleteLocal(BLOB)),
        actual = plan(
            local = listOf(LocalFileState(NOTE, "a"), LocalFileState(BLOB, localImageHash(10))),
            remote = listOf(RemoteFileState(NOTE, "r1", contentHash = null)),
            index = mapOf(NOTE to SyncIndexEntry("a", "r1"), BLOB to SyncIndexEntry(localImageHash(10), "r1")),
            notes = mapOf(NOTE to "plain".encodeToByteArray()),
        ).operations,
    )

    @Test
    fun `no content file is deleted in a pass that brings a note down`() {
        val plan = plan(
            local = listOf(LocalFileState(BLOB, localImageHash(10))),
            remote = listOf(RemoteFileState(NOTE, "r1", contentHash = null)),
            index = mapOf(BLOB to SyncIndexEntry(localImageHash(10), "r1")),
            notes = emptyMap(),
        )

        assertEquals(listOf<SyncOperation>(SyncOperation.Download(NOTE, "r1")), plan.operations)
        assertTrue(plan.holdsDeletionsBack)
    }

    @Test
    fun `a checklist coming down holds no deletion back as checklists name nothing`() {
        val plan = plan(
            local = listOf(LocalFileState(BLOB, localImageHash(10))),
            remote = listOf(RemoteFileState(CHECKLIST, "r1", contentHash = null)),
            index = mapOf(BLOB to SyncIndexEntry(localImageHash(10), "r1")),
            notes = emptyMap(),
        )

        assertEquals(setOf(SyncOperation.Download(CHECKLIST, "r1"), SyncOperation.DeleteLocal(BLOB)), plan.operations.toSet())
        assertFalse(plan.holdsDeletionsBack)
    }

    /** Plans with what the notes held here ([notes], by their bytes) name, read by the other app's own [SyncReferences]. */
    private fun plan(
        local: List<LocalFileState>,
        remote: List<RemoteFileState>,
        index: Map<SyncKey, SyncIndexEntry>,
        notes: Map<SyncKey, ByteArray>,
    ) = SyncPlanner.planned(
        local = local,
        remote = remote,
        index = index,
        imageReferences = notes.mapValues { (key, bytes) -> OtherAppLibrary.references.getValue(key.kind).of(bytes) },
        referencing = OtherAppLibrary.references.keys,
    )

    private companion object {
        val NOTE = note("groceries")
        val CHECKLIST = checklist("packing")
        val BLOB = SyncKey(BLOBS, "0123456789abcdef.png")
        val NAMING = "Groceries;see:${BLOB.name};".encodeToByteArray()
    }
}
