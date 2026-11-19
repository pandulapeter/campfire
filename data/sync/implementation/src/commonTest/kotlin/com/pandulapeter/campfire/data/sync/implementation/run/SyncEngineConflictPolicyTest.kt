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

import com.pandulapeter.campfire.data.sync.implementation.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.indexOf
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.CHECKLISTS
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.NOTES
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.checklist
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.note
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.synchronize
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Another app's collection settles a conflict as its own [ConflictPolicy] says, and a collection without one keeps both
 * versions of every conflict.
 */
class SyncEngineConflictPolicyTest {

    @Test
    fun `a checklist that differs only in its volatile part takes the folder's version and keeps no copy`() = runTest {
        val there = "Packing;ticked:monday;items:a".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(mapOf(CHECKLIST to "Packing;ticked:tuesday;items:a".encodeToByteArray()))
        val cloud = FakeSyncProvider(mapOf(CHECKLIST to there))

        val result = synchronize(local, cloud, indexOf(CHECKLIST to "Packing;items:a".encodeToByteArray()))

        assertContentEquals(there, local.files[CHECKLIST])
        assertEquals(setOf(CHECKLIST), local.files.keys)
        assertEquals(setOf(CHECKLIST), cloud.files.keys)
        assertEquals(emptyList(), result.summary.conflicts)
    }

    @Test
    fun `a checklist whose only change here is its volatile part takes the folder's edit`() = runTest {
        val there = "Packing;items:a,b".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(mapOf(CHECKLIST to "Packing;ticked:tuesday;items:a".encodeToByteArray()))
        val cloud = FakeSyncProvider(mapOf(CHECKLIST to there))

        val result = synchronize(local, cloud, indexOf(CHECKLIST to "Packing;items:a".encodeToByteArray()))

        assertContentEquals(there, local.files[CHECKLIST])
        assertEquals(setOf(CHECKLIST), local.files.keys)
        assertEquals(emptyList(), result.summary.conflicts)
    }

    @Test
    fun `a checklist changed in more than its volatile part keeps both`() = runTest {
        val here = "Packing;ticked:tuesday;items:a,b".encodeToByteArray()
        val there = "Packing;ticked:monday;items:a,c".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(mapOf(CHECKLIST to here))
        val cloud = FakeSyncProvider(mapOf(CHECKLIST to there))

        val result = synchronize(local, cloud, indexOf(CHECKLIST to "Packing;items:a".encodeToByteArray()))

        val copy = SyncKey(kind = CHECKLISTS, name = "packing (2).list")
        assertContentEquals(here, local.files[CHECKLIST])
        assertContentEquals(there, local.files[copy])
        assertEquals(listOf(copy.name), result.summary.conflicts)
    }

    @Test
    fun `a note with no policy keeps both even where it differs only in what a checklist's policy ignores`() = runTest {
        val here = "Groceries;ticked:tuesday;".encodeToByteArray()
        val there = "Groceries;ticked:monday;".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(mapOf(NOTE to here))
        val cloud = FakeSyncProvider(mapOf(NOTE to there))

        val result = synchronize(local, cloud, indexOf(NOTE to "Groceries;".encodeToByteArray()))

        val copy = SyncKey(kind = NOTES, name = "groceries (2).note")
        assertContentEquals(here, local.files[NOTE])
        assertContentEquals(there, local.files[copy])
        assertEquals(listOf(copy.name), result.summary.conflicts)
    }

    private companion object {
        val CHECKLIST = checklist("packing")
        val NOTE = note("groceries")
    }
}
