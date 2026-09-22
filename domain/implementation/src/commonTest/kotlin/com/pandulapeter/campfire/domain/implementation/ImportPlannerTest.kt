/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation

import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ImportPlannerTest {

    @Test
    fun songsAreComparedAcrossTheirWholeCollisionFamily() = runTest {
        val plan = plan(
            library = mapOf("x.cho" to A, "x_2.cho" to B, "x (3).crd" to C),
            song(text = A, sourceFileName = "x.cho"),
            song(text = B, sourceFileName = "x_2.cho"),
            song(text = C),
        )

        assertEquals(listOf("x.cho", "x (3).crd", "x_2.cho"), plan.map { it.fileName })
        assertTrue(plan.all { it.status == ImportPlan.Status.IDENTICAL })
    }

    @Test
    fun batchDuplicatesFollowTheFirstEntryAndOnlyOneEntryConflicts() = runTest {
        val plan = plan(
            library = mapOf("x.cho" to A),
            song(text = B),
            song(text = C),
            song(text = B),
        )

        assertEquals(listOf(ImportPlan.Status.CONFLICTING, ImportPlan.Status.NEW, ImportPlan.Status.IDENTICAL), plan.map { it.status })
        assertEquals(0, plan.last().repeatedEntryIndex)
        assertEquals(listOf("x.cho"), ImportPlan(songs = plan).summary.conflictingFileNames)
    }

    @Test
    fun numberedArrivalsAreHandledInTheirLibraryOrderAndOnlyReadTheirFamily() = runTest {
        val reads = mutableListOf<String>()
        val library = mapOf("x.cho" to A, "x_2.cho" to B, "y.cho" to C, "xylophone.cho" to C)
        val plan = ImportPlanner.planSongs(
            incoming = listOf(song(text = B, sourceFileName = "x_2.cho"), song(text = A, sourceFileName = "x.cho")),
            libraryFileNames = library.keys,
            readLibraryText = { fileName -> reads += fileName; library[fileName] },
        )

        assertEquals(listOf("x.cho", "x_2.cho"), plan.map { it.fileName })
        assertEquals(setOf("x.cho", "x_2.cho"), reads.toSet())
    }

    @Test
    fun setlistsUseFamiliesAndDoNotConflictWithTheirBatchSiblings() {
        val first = setlist(fileName = "summer_set.setlist.json", title = "Summer set")
        val second = setlist(fileName = "summer_set_2.setlist.json", title = "Summer Set!")
        val planned = ImportPlanner.planSetlists(
            incoming = listOf(
                ImportPlanner.IncomingSetlist(second, "summer_set_2.setlist.json"),
                ImportPlanner.IncomingSetlist(first, "summer_set.setlist.json"),
            ),
            librarySetlists = emptyList(),
        )

        assertEquals(listOf(ImportPlan.Status.NEW, ImportPlan.Status.NEW), planned.map { it.status })
        assertFalse(ImportPlan(setlists = planned).hasConflicts)
    }

    @Test
    fun aSetlistThatDiffersOnlyByAFieldThisVersionDoesNotKnowIsNotTheSame() {
        val library = setlist(fileName = "summer_set.setlist.json", title = "Summer set")
        val incoming = library.copy(unknownFields = """{"venue":"x"}""")

        val planned = ImportPlanner.planSetlists(
            incoming = listOf(ImportPlanner.IncomingSetlist(incoming, "summer_set.setlist.json")),
            librarySetlists = listOf(library),
        )

        assertEquals(listOf(ImportPlan.Status.CONFLICTING), planned.map { it.status })
    }

    private suspend fun plan(library: Map<String, String>, vararg incoming: ImportPlanner.IncomingSong) =
        ImportPlanner.planSongs(incoming.toList(), library.keys) { library[it] }

    private fun song(text: String, sourceFileName: String? = null) =
        ImportPlanner.IncomingSong(fileName = "x.cho", text = text, sourceFileName = sourceFileName)

    private fun setlist(fileName: String, title: String) = Setlist(
        fileName = fileName,
        title = title,
        description = "",
        priority = 0,
        isArchived = false,
        entries = emptyList(),
        size = 0L,
    )

    private companion object {
        const val A = "{title: A}\nA\n"
        const val B = "{title: B}\nB\n"
        const val C = "{title: C}\nC\n"
    }
}
