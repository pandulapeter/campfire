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
import com.pandulapeter.campfire.domain.implementation.ImportPlanner.withSongFileNames
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
            songFileNames = emptyMap(),
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
            songFileNames = emptyMap(),
        )

        assertEquals(listOf(ImportPlan.Status.CONFLICTING), planned.map { it.status })
    }

    @Test
    fun aSongIsRecognizedUnderTheLibraryNameItArrivedWith() = runTest {
        // Exported under a name the library gave it before the naming rule changed, and named by its header now.
        val plan = plan(library = mapOf("old_name.cho" to A), song(text = A, sourceFileName = "old_name.cho"))

        assertEquals(listOf("old_name.cho"), plan.map { it.fileName })
        assertEquals(listOf(ImportPlan.Status.IDENTICAL), plan.map { it.status })
    }

    @Test
    fun aDifferentSongUnderTheNameItArrivedWithIsNew() = runTest {
        val plan = plan(library = mapOf("old_name.cho" to B), song(text = A, sourceFileName = "old_name.cho"))

        assertEquals(listOf("x.cho"), plan.map { it.fileName })
        assertEquals(listOf(ImportPlan.Status.NEW), plan.map { it.status })
    }

    @Test
    fun aSetlistIsRecognizedUnderTheLibraryNameItArrivedWith() {
        val library = setlist(fileName = "old_name.setlist.json", title = "Summer set")
        val planned = ImportPlanner.planSetlists(
            incoming = listOf(ImportPlanner.IncomingSetlist(library.copy(fileName = "summer_set.setlist.json"), "old_name.setlist.json")),
            librarySetlists = listOf(library),
            songFileNames = emptyMap(),
        )

        assertEquals(listOf("old_name.setlist.json"), planned.map { it.fileName })
        assertEquals(listOf(ImportPlan.Status.IDENTICAL), planned.map { it.status })
    }

    @Test
    fun aSetlistFollowingAnEditedSongKeptNextToTheLibrarysIsANewSetlist() = runTest {
        val (songs, setlists) = planEditedSongWithItsSetlist()
        assertEquals(listOf(ImportPlan.Status.CONFLICTING), songs.map { it.status })
        // Before anything is written, the plan expects the song in place, which is what replacing and skipping do.
        assertEquals(listOf(ImportPlan.Status.IDENTICAL), setlists.map { it.status })

        // Kept both, the song is written as song_2.cho, and the setlist pointing at it is not the library's any more.
        val replanned = ImportPlanner.replanSetlists(
            planned = setlists,
            librarySetlists = listOf(SONG_SETLIST),
            songFileNames = mapOf("song.cho" to "song_2.cho"),
        )

        assertEquals(listOf(ImportPlan.Status.CONFLICTING), replanned.map { it.status })
        val written = replanned.single().setlist.withSongFileNames(mapOf("song.cho" to "song_2.cho"))
        assertEquals(listOf("song_2.cho"), written.entries.map { it.songFileName })
    }

    @Test
    fun aSetlistFollowingAnEditedSongThatReplacedOrLeftTheLibrarysIsTheSame() = runTest {
        val (_, setlists) = planEditedSongWithItsSetlist()

        // Replacing writes the song over song.cho and skipping leaves song.cho alone: either way it is where it was.
        val replanned = ImportPlanner.replanSetlists(
            planned = setlists,
            librarySetlists = listOf(SONG_SETLIST),
            songFileNames = mapOf("song.cho" to "song.cho"),
        )

        assertEquals(listOf(ImportPlan.Status.IDENTICAL), replanned.map { it.status })
        assertEquals(listOf(SONG_SETLIST.fileName), replanned.map { it.fileName })
    }

    @Test
    fun aSetlistIsComparedPointingWhereItsSongsLand() = runTest {
        // The song arrives under a name its header does not give it, and the library already holds it as song_2.cho.
        val songs = ImportPlanner.planSongs(
            incoming = listOf(ImportPlanner.IncomingSong(fileName = "song.cho", text = B, sourceFileName = "Song.cho")),
            libraryFileNames = listOf("song.cho", "song_2.cho"),
            readLibraryText = { mapOf("song.cho" to A, "song_2.cho" to B)[it] },
        )
        val library = SONG_SETLIST.copy(entries = listOf(Setlist.Entry(songFileName = "song_2.cho")))
        val setlists = ImportPlanner.planSetlists(
            incoming = listOf(ImportPlanner.IncomingSetlist(SONG_SETLIST.withSongFileNames(mapOf("song.cho" to "Song.cho")), SONG_SETLIST.fileName)),
            librarySetlists = listOf(library),
            songFileNames = ImportPlanner.plannedSongFileNames(songs),
        )

        assertEquals(listOf(ImportPlan.Status.IDENTICAL), songs.map { it.status })
        assertEquals(mapOf("Song.cho" to "song_2.cho"), ImportPlanner.plannedSongFileNames(songs))
        assertEquals(listOf(ImportPlan.Status.IDENTICAL), setlists.map { it.status })
    }

    @Test
    fun aSetlistWithItsNewSongIsNewAndPointsAtIt() = runTest {
        val songs = ImportPlanner.planSongs(
            incoming = listOf(ImportPlanner.IncomingSong(fileName = "song.cho", text = A, sourceFileName = "Song.cho")),
            libraryFileNames = emptyList(),
            readLibraryText = { null },
        )
        val songFileNames = ImportPlanner.plannedSongFileNames(songs)
        val setlists = ImportPlanner.planSetlists(
            incoming = listOf(ImportPlanner.IncomingSetlist(SONG_SETLIST.withSongFileNames(mapOf("song.cho" to "Song.cho")), SONG_SETLIST.fileName)),
            librarySetlists = emptyList(),
            songFileNames = songFileNames,
        )

        assertEquals(listOf(ImportPlan.Status.NEW), songs.map { it.status })
        assertEquals(listOf(ImportPlan.Status.NEW), setlists.map { it.status })
        assertFalse(ImportPlan(songs = songs, setlists = setlists).hasConflicts)
        assertEquals(listOf("song.cho"), setlists.single().setlist.withSongFileNames(songFileNames).entries.map { it.songFileName })
    }

    /** The library holds song.cho and a setlist naming it; the import brings an edited song.cho and the same setlist. */
    private suspend fun planEditedSongWithItsSetlist(): Pair<List<ImportPlan.SongEntry>, List<ImportPlan.SetlistEntry>> {
        val songs = ImportPlanner.planSongs(
            incoming = listOf(ImportPlanner.IncomingSong(fileName = "song.cho", text = B, sourceFileName = "song.cho")),
            libraryFileNames = listOf("song.cho"),
            readLibraryText = { mapOf("song.cho" to A)[it] },
        )
        val setlists = ImportPlanner.planSetlists(
            incoming = listOf(ImportPlanner.IncomingSetlist(SONG_SETLIST, SONG_SETLIST.fileName)),
            librarySetlists = listOf(SONG_SETLIST),
            songFileNames = ImportPlanner.plannedSongFileNames(songs),
        )
        return songs to setlists
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
        val SONG_SETLIST = Setlist(
            fileName = "set.setlist.json",
            title = "Set",
            description = "",
            priority = 0,
            isArchived = false,
            entries = listOf(Setlist.Entry(songFileName = "song.cho")),
            size = 0L,
        )
        const val A = "{title: A}\nA\n"
        const val B = "{title: B}\nB\n"
        const val C = "{title: C}\nC\n"
    }
}
