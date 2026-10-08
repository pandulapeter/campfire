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
import com.pandulapeter.campfire.data.model.domain.normalizedToNfc
import com.pandulapeter.campfire.domain.implementation.ImportPlanner.withSongFileNames
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ImportPlannerTest {

    @Test
    fun `songs are compared across their whole collision family`() = runTest {
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
    fun `batch duplicates follow the first entry and only one entry conflicts`() = runTest {
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
    fun `numbered arrivals are handled in their library order and only read their family`() = runTest {
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
    fun `songs arriving as older named library files read each file once and in parallel`() = runTest {
        val library = (1..200).associate { "Artist - Title $it.cho" to "{title: Title $it}\n" }
        val reads = mutableListOf<String>()
        var running = 0
        var maximumRunning = 0
        val plan = ImportPlanner.planSongs(
            incoming = library.map { (fileName, text) ->
                ImportPlanner.IncomingSong(fileName = "title_${fileName.filter(Char::isDigit)}.cho", text = text, sourceFileName = fileName)
            },
            libraryFileNames = library.keys,
            readLibraryText = { fileName ->
                reads += fileName
                // Only the library files are counted: the names the headers give are prefetched in parallel either way.
                val isLibraryFile = fileName in library
                if (isLibraryFile) maximumRunning = maxOf(maximumRunning, ++running)
                yield()
                if (isLibraryFile) running--
                library[fileName]
            },
        )

        assertTrue(plan.all { it.status == ImportPlan.Status.IDENTICAL })
        assertEquals(library.keys.toList(), plan.map { it.fileName })
        // The names the headers give are read too, as the family of each; the library files are what must be read once.
        assertEquals(library.keys.sorted(), reads.filter { it in library }.sorted())
        assertTrue(maximumRunning > 1)
    }

    @Test
    fun `setlists use families and do not conflict with their batch siblings`() {
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
    fun `a setlist that differs only by a field this version does not know is not the same`() {
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
    fun `a setlist that carries no date is the same as the library's dated one`() {
        // The bundled demo setlist names no day, and the copy the library holds was dated by the import that planted it.
        val library = setlist(fileName = "summer_set.setlist.json", title = "Summer set").copy(date = LocalDate(2026, 9, 28))

        fun statusOf(incoming: Setlist, isDated: Boolean) = ImportPlanner.planSetlists(
            incoming = listOf(ImportPlanner.IncomingSetlist(incoming, "summer_set.setlist.json", isDated = isDated)),
            librarySetlists = listOf(library),
            songFileNames = emptyMap(),
        ).single().status

        // An undated file is parsed with the day it is imported on, which is not the day the library's copy holds.
        assertEquals(ImportPlan.Status.IDENTICAL, statusOf(library.copy(date = LocalDate(2026, 10, 1)), isDated = false))
        assertEquals(ImportPlan.Status.CONFLICTING, statusOf(library.copy(date = LocalDate(2026, 10, 1)), isDated = true))
    }

    @Test
    fun `a setlist that carries no date says nothing about the countdown either`() {
        val library = setlist(fileName = "summer_set.setlist.json", title = "Summer set").copy(date = LocalDate(2026, 9, 28), isCountdownShown = true)

        fun statusOf(incoming: Setlist, isDated: Boolean) = ImportPlanner.planSetlists(
            incoming = listOf(ImportPlanner.IncomingSetlist(incoming, "summer_set.setlist.json", isDated = isDated)),
            librarySetlists = listOf(library),
            songFileNames = emptyMap(),
        ).single().status

        assertEquals(ImportPlan.Status.IDENTICAL, statusOf(library.copy(date = LocalDate(2026, 10, 1), isCountdownShown = false), isDated = false))
        // A file that carries a date was written by a build that knew the countdown, so its flag is what it says.
        assertEquals(ImportPlan.Status.CONFLICTING, statusOf(library.copy(isCountdownShown = false), isDated = true))
    }

    @Test
    fun `a song is recognized under the library name it arrived with`() = runTest {
        // Exported under a name the library gave it before the naming rule changed, and named by its header now.
        val plan = plan(library = mapOf("old_name.cho" to A), song(text = A, sourceFileName = "old_name.cho"))

        assertEquals(listOf("old_name.cho"), plan.map { it.fileName })
        assertEquals(listOf(ImportPlan.Status.IDENTICAL), plan.map { it.status })
    }

    @Test
    fun `a different song under the name it arrived with is new`() = runTest {
        val plan = plan(library = mapOf("old_name.cho" to B), song(text = A, sourceFileName = "old_name.cho"))

        assertEquals(listOf("x.cho"), plan.map { it.fileName })
        assertEquals(listOf(ImportPlan.Status.NEW), plan.map { it.status })
    }

    @Test
    fun `a setlist is recognized under the library name it arrived with`() {
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
    fun `a setlist following an edited song kept next to the library's is a new setlist`() = runTest {
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
    fun `a setlist following an edited song that replaced or left the library's is the same`() = runTest {
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
    fun `a setlist is compared pointing where its songs land`() = runTest {
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
        assertEquals(mapOf("Song.cho" to "song_2.cho"), ImportPlanner.plannedSongFileNames(songs).asMap())
        assertEquals(listOf(ImportPlan.Status.IDENTICAL), setlists.map { it.status })
    }

    @Test
    fun `a setlist whose two entries land on one song is the library's own on reimport`() = runTest {
        val songs = ImportPlanner.planSongs(
            incoming = listOf(song(A, sourceFileName = "x.cho"), song(A, sourceFileName = "x_2.cho")),
            libraryFileNames = listOf("x.cho"),
            readLibraryText = mapOf("x.cho" to A).exact(),
        )
        val library = SONG_SETLIST.copy(entries = listOf(Setlist.Entry(songFileName = "x.cho")))
        val incoming = SONG_SETLIST.copy(entries = listOf(Setlist.Entry(songFileName = "x.cho"), Setlist.Entry(songFileName = "x_2.cho")))
        val setlists = ImportPlanner.planSetlists(
            incoming = listOf(ImportPlanner.IncomingSetlist(incoming, SONG_SETLIST.fileName)),
            librarySetlists = listOf(library),
            songFileNames = ImportPlanner.plannedSongFileNames(songs),
        )

        assertEquals(listOf(ImportPlan.Status.IDENTICAL), setlists.map { it.status })
    }

    @Test
    fun `a setlist with its new song is new and points at it`() = runTest {
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
        assertEquals(listOf("song.cho"), setlists.single().setlist.withSongFileNames(songFileNames.asMap()).entries.map { it.songFileName })
    }

    @Test
    fun `a different song wanting a name the batch brings back unchanged is new`() = runTest {
        val plan = plan(
            library = mapOf("x.cho" to A),
            song(text = A, sourceFileName = "x.cho"),
            song(text = B, sourceFileName = "x_2.cho"),
        )

        assertEquals(listOf(ImportPlan.Status.IDENTICAL, ImportPlan.Status.NEW), plan.map { it.status })
        assertEquals(listOf("x.cho", "x.cho"), plan.map { it.fileName })
        assertFalse(ImportPlan(songs = plan).hasConflicts)
        assertEquals(mapOf("x.cho" to "x.cho", "x_2.cho" to "x.cho"), ImportPlanner.plannedSongFileNames(plan).asMap())
    }

    @Test
    fun `a conflict copy given before the library's song is new`() = runTest {
        val plan = plan(
            library = mapOf("x.cho" to A),
            song(text = B, sourceFileName = "x (2).cho"),
            song(text = A, sourceFileName = "x.cho"),
        )

        assertEquals(listOf(A, B), plan.map { it.text })
        assertEquals(listOf(ImportPlan.Status.IDENTICAL, ImportPlan.Status.NEW), plan.map { it.status })
        assertFalse(ImportPlan(songs = plan).hasConflicts)
    }

    @Test
    fun `a different song is new whichever side of the library's song it arrives on`() = runTest {
        val libraryFirst = plan(library = mapOf("x.cho" to A), song(text = A), song(text = B))
        val libraryLast = plan(library = mapOf("x.cho" to A), song(text = B), song(text = A))

        assertEquals(listOf(ImportPlan.Status.IDENTICAL, ImportPlan.Status.NEW), libraryFirst.map { it.status })
        assertEquals(listOf(ImportPlan.Status.NEW, ImportPlan.Status.IDENTICAL), libraryLast.map { it.status })
        assertFalse(ImportPlan(songs = libraryFirst).hasConflicts)
        assertFalse(ImportPlan(songs = libraryLast).hasConflicts)
    }

    @Test
    fun `repeats around the library's song follow the first of them`() = runTest {
        val repeatAfterLibrary = plan(library = mapOf("x.cho" to A), song(text = B), song(text = A), song(text = B))
        val repeatBeforeLibrary = plan(library = mapOf("x.cho" to A), song(text = A), song(text = B), song(text = B))

        assertEquals(
            listOf(ImportPlan.Status.NEW, ImportPlan.Status.IDENTICAL, ImportPlan.Status.IDENTICAL),
            repeatAfterLibrary.map { it.status },
        )
        assertEquals(0, repeatAfterLibrary.last().repeatedEntryIndex)
        assertEquals(
            listOf(ImportPlan.Status.IDENTICAL, ImportPlan.Status.NEW, ImportPlan.Status.IDENTICAL),
            repeatBeforeLibrary.map { it.status },
        )
        assertEquals(1, repeatBeforeLibrary.last().repeatedEntryIndex)
        assertFalse(ImportPlan(songs = repeatAfterLibrary).hasConflicts)
        assertFalse(ImportPlan(songs = repeatBeforeLibrary).hasConflicts)
    }

    @Test
    fun `two different songs before the library's song are both new`() = runTest {
        val plan = plan(library = mapOf("x.cho" to A), song(text = B), song(text = C), song(text = A))

        assertEquals(listOf(ImportPlan.Status.NEW, ImportPlan.Status.NEW, ImportPlan.Status.IDENTICAL), plan.map { it.status })
        assertFalse(ImportPlan(songs = plan).hasConflicts)
    }

    @Test
    fun `a different song is still a question when the batch does not bring the library's song`() = runTest {
        val plan = plan(library = mapOf("x.cho" to A), song(text = B))

        assertEquals(listOf(ImportPlan.Status.CONFLICTING), plan.map { it.status })
    }

    @Test
    fun `a numbered sibling the batch brings back protects only itself`() = runTest {
        val plan = plan(
            library = mapOf("x.cho" to A, "x_2.cho" to B),
            song(text = B, sourceFileName = "x_2.cho"),
            song(text = C),
        )

        assertEquals(listOf(C, B), plan.map { it.text })
        assertEquals(listOf(ImportPlan.Status.CONFLICTING, ImportPlan.Status.IDENTICAL), plan.map { it.status })
        assertEquals("x_2.cho", plan.last().fileName)
        assertEquals(listOf("x.cho"), ImportPlan(songs = plan).summary.conflictingFileNames)
    }

    @Test
    fun `a song wanting the name a library song arrived under is new in either order`() = runTest {
        val renamed = ImportPlanner.IncomingSong(fileName = "y.cho", text = A, sourceFileName = "x.cho")
        val different = ImportPlanner.IncomingSong(fileName = "x.cho", text = B, sourceFileName = null)
        val library = mapOf("x.cho" to A)

        val renamedFirst = ImportPlanner.planSongs(listOf(renamed, different), library.keys) { library[it] }
        val renamedLast = ImportPlanner.planSongs(listOf(different, renamed), library.keys) { library[it] }

        assertEquals(listOf(ImportPlan.Status.IDENTICAL, ImportPlan.Status.NEW), renamedFirst.map { it.status })
        assertEquals("x.cho", renamedFirst.first().fileName)
        assertEquals(listOf(ImportPlan.Status.NEW, ImportPlan.Status.IDENTICAL), renamedLast.map { it.status })
        assertEquals("x.cho", renamedLast.last().fileName)
        assertFalse(ImportPlan(songs = renamedFirst).hasConflicts)
        assertFalse(ImportPlan(songs = renamedLast).hasConflicts)
    }

    @Test
    fun `a different setlist wanting a name the batch brings back unchanged is new`() {
        val library = setlist(fileName = "summer.setlist.json", title = "Summer")
        val different = library.copy(entries = listOf(Setlist.Entry(songFileName = "song.cho")))
        fun planSetlists(vararg incoming: Setlist) = ImportPlanner.planSetlists(
            incoming = incoming.map { ImportPlanner.IncomingSetlist(it, it.fileName) },
            librarySetlists = listOf(library),
            songFileNames = emptyMap(),
        )

        val libraryFirst = planSetlists(library, different)
        val libraryLast = planSetlists(different, library)

        assertEquals(listOf(ImportPlan.Status.IDENTICAL, ImportPlan.Status.NEW), libraryFirst.map { it.status })
        assertEquals(listOf(ImportPlan.Status.NEW, ImportPlan.Status.IDENTICAL), libraryLast.map { it.status })
        assertFalse(ImportPlan(setlists = libraryFirst).hasConflicts)
        assertFalse(ImportPlan(setlists = libraryLast).hasConflicts)
    }

    @Test
    fun `a numbered setlist sibling the batch brings back protects only itself`() {
        val first = setlist(fileName = "summer.setlist.json", title = "Summer")
        val second = first.copy(fileName = "summer_2.setlist.json", entries = listOf(Setlist.Entry(songFileName = "a.cho")))
        val different = first.copy(entries = listOf(Setlist.Entry(songFileName = "b.cho")))
        val planned = ImportPlanner.planSetlists(
            incoming = listOf(
                ImportPlanner.IncomingSetlist(second.copy(fileName = first.fileName), second.fileName),
                ImportPlanner.IncomingSetlist(different, different.fileName),
            ),
            librarySetlists = listOf(first, second),
            songFileNames = emptyMap(),
        )

        assertEquals(listOf(different.entries, second.entries), planned.map { it.setlist.entries })
        assertEquals(listOf(ImportPlan.Status.CONFLICTING, ImportPlan.Status.IDENTICAL), planned.map { it.status })
        assertEquals(second.fileName, planned.last().fileName)
    }

    @Test
    fun `a setlist wanting the name a library setlist arrived under is new`() {
        val old = setlist(fileName = "old_name.setlist.json", title = "Summer")
        val different = setlist(fileName = "old_name.setlist.json", title = "Old name")
        val planned = ImportPlanner.planSetlists(
            incoming = listOf(
                ImportPlanner.IncomingSetlist(old.copy(fileName = "summer.setlist.json"), old.fileName),
                ImportPlanner.IncomingSetlist(different, "other.setlist.json"),
            ),
            librarySetlists = listOf(old),
            songFileNames = emptyMap(),
        )

        assertEquals(listOf(ImportPlan.Status.IDENTICAL, ImportPlan.Status.NEW), planned.map { it.status })
        assertEquals(old.fileName, planned.first().fileName)
    }

    @Test
    fun `a setlist that stops being the library's on replan is never replaced`() = runTest {
        val other = SONG_SETLIST.copy(entries = listOf(Setlist.Entry(songFileName = "other.cho")))
        val songs = planEditedSong()
        val planned = ImportPlanner.planSetlists(
            incoming = listOf(
                ImportPlanner.IncomingSetlist(SONG_SETLIST, SONG_SETLIST.fileName),
                ImportPlanner.IncomingSetlist(other, other.fileName),
            ),
            librarySetlists = listOf(SONG_SETLIST),
            songFileNames = ImportPlanner.plannedSongFileNames(songs),
        )
        assertEquals(listOf(ImportPlan.Status.IDENTICAL, ImportPlan.Status.NEW), planned.map { it.status })

        val replanned = ImportPlanner.replanSetlists(
            planned = planned,
            librarySetlists = listOf(SONG_SETLIST),
            songFileNames = mapOf("song.cho" to "song_2.cho"),
        )

        assertEquals(listOf(ImportPlan.Status.CONFLICTING, ImportPlan.Status.NEW), replanned.map { it.status })
        // Only an entry the user was asked about and is still in question is ever replaced.
        assertTrue(
            planned.zip(replanned).none { (before, after) ->
                before.status == ImportPlan.Status.CONFLICTING && after.status == ImportPlan.Status.CONFLICTING
            }
        )
    }

    @Test
    fun `a conflicting setlist the replan finds brought back is no longer a question`() = runTest {
        val library = SONG_SETLIST.copy(entries = listOf(Setlist.Entry(songFileName = "song_2.cho")))
        val other = SONG_SETLIST.copy(entries = listOf(Setlist.Entry(songFileName = "other.cho")))
        val songs = planEditedSong()
        val planned = ImportPlanner.planSetlists(
            incoming = listOf(
                ImportPlanner.IncomingSetlist(SONG_SETLIST, SONG_SETLIST.fileName),
                ImportPlanner.IncomingSetlist(other, other.fileName),
            ),
            librarySetlists = listOf(library),
            songFileNames = ImportPlanner.plannedSongFileNames(songs),
        )
        assertEquals(listOf(ImportPlan.Status.CONFLICTING, ImportPlan.Status.NEW), planned.map { it.status })

        val replanned = ImportPlanner.replanSetlists(
            planned = planned,
            librarySetlists = listOf(library),
            songFileNames = mapOf("song.cho" to "song_2.cho"),
        )

        assertEquals(listOf(ImportPlan.Status.IDENTICAL, ImportPlan.Status.NEW), replanned.map { it.status })
    }

    @Test
    fun `an identical song is recorded under the spelling the library lists`() = runTest {
        val library = mapOf("Wonderwall.cho" to A)
        val incoming = listOf(ImportPlanner.IncomingSong(fileName = "wonderwall.cho", text = A, sourceFileName = "Wonderwall.cho"))

        listOf(library.folding(), library.exact()).forEach { read ->
            val plan = ImportPlanner.planSongs(incoming, library.keys, read)

            assertEquals(listOf(ImportPlan.Status.IDENTICAL), plan.map { it.status })
            assertEquals(listOf("Wonderwall.cho"), plan.map { it.fileName })
            assertEquals(mapOf("Wonderwall.cho" to "Wonderwall.cho"), ImportPlanner.plannedSongFileNames(plan).asMap())
        }
    }

    @Test
    fun `a different song replaces the spelling the library lists where the file system folds case`() = runTest {
        val library = mapOf("Wonderwall.cho" to A)
        val incoming = listOf(ImportPlanner.IncomingSong(fileName = "wonderwall.cho", text = B, sourceFileName = null))

        val plan = ImportPlanner.planSongs(incoming, library.keys, library.folding())

        assertEquals(listOf(ImportPlan.Status.CONFLICTING), plan.map { it.status })
        assertEquals("wonderwall.cho", plan.single().fileName)
        assertEquals("Wonderwall.cho", plan.single().replacesFileName)
        assertEquals(listOf("Wonderwall.cho"), ImportPlan(songs = plan).summary.conflictingFileNames)
    }

    @Test
    fun `a different song is a name of its own where the file system tells case apart`() = runTest {
        val library = mapOf("Wonderwall.cho" to A)
        val incoming = listOf(ImportPlanner.IncomingSong(fileName = "wonderwall.cho", text = B, sourceFileName = null))

        val plan = ImportPlanner.planSongs(incoming, library.keys, library.exact())

        assertEquals(listOf(ImportPlan.Status.NEW), plan.map { it.status })
        assertNull(plan.single().replacesFileName)
    }

    @Test
    fun `a song listed in decomposed form is a member of its composed family`() = runTest {
        // "йога", its first letter written as и and a combining breve, the way APFS may list it.
        val library = mapOf("\u0438\u0306\u043e\u0433\u0430.cho" to A)
        val incoming = listOf(ImportPlanner.IncomingSong(fileName = "\u0439\u043e\u0433\u0430.cho", text = A, sourceFileName = null))

        listOf(library.folding(), library.exact()).forEach { read ->
            val plan = ImportPlanner.planSongs(incoming, library.keys, read)

            assertEquals(listOf(ImportPlan.Status.IDENTICAL), plan.map { it.status })
            assertEquals(listOf(library.keys.single()), plan.map { it.fileName })
        }
    }

    @Test
    fun `a different song next to the library's under another spelling is new`() = runTest {
        val library = mapOf("Wonderwall.cho" to A)
        val plan = ImportPlanner.planSongs(
            incoming = listOf(
                ImportPlanner.IncomingSong(fileName = "wonderwall.cho", text = A, sourceFileName = "Wonderwall.cho"),
                ImportPlanner.IncomingSong(fileName = "wonderwall.cho", text = B, sourceFileName = null),
            ),
            libraryFileNames = library.keys,
            readLibraryText = library.folding(),
        )

        assertEquals(listOf(ImportPlan.Status.IDENTICAL, ImportPlan.Status.NEW), plan.map { it.status })
        assertEquals("Wonderwall.cho", plan.first().fileName)
        assertFalse(ImportPlan(songs = plan).hasConflicts)
    }

    @Test
    fun `a setlist follows its song to the spelling the library lists`() = runTest {
        val library = mapOf("Wonderwall.cho" to A)
        val songs = ImportPlanner.planSongs(
            incoming = listOf(ImportPlanner.IncomingSong(fileName = "wonderwall.cho", text = A, sourceFileName = "Wonderwall.cho")),
            libraryFileNames = library.keys,
            readLibraryText = library.folding(),
        )
        val setlist = SONG_SETLIST.copy(entries = listOf(Setlist.Entry(songFileName = "Wonderwall.cho")))
        val planned = ImportPlanner.planSetlists(
            incoming = listOf(ImportPlanner.IncomingSetlist(setlist, setlist.fileName)),
            librarySetlists = listOf(setlist),
            songFileNames = ImportPlanner.plannedSongFileNames(songs),
        )

        assertEquals(listOf(ImportPlan.Status.IDENTICAL), planned.map { it.status })
    }

    /** The library holds song.cho and a setlist naming it; the import brings an edited song.cho and the same setlist. */
    private suspend fun planEditedSongWithItsSetlist(): Pair<List<ImportPlan.SongEntry>, List<ImportPlan.SetlistEntry>> {
        val songs = planEditedSong()
        val setlists = ImportPlanner.planSetlists(
            incoming = listOf(ImportPlanner.IncomingSetlist(SONG_SETLIST, SONG_SETLIST.fileName)),
            librarySetlists = listOf(SONG_SETLIST),
            songFileNames = ImportPlanner.plannedSongFileNames(songs),
        )
        return songs to setlists
    }

    /** The library holds song.cho; the import brings an edited song.cho. */
    private suspend fun planEditedSong() = ImportPlanner.planSongs(
        incoming = listOf(ImportPlanner.IncomingSong(fileName = "song.cho", text = B, sourceFileName = "song.cho")),
        libraryFileNames = listOf("song.cho"),
        readLibraryText = { mapOf("song.cho" to A)[it] },
    )

    private suspend fun plan(library: Map<String, String>, vararg incoming: ImportPlanner.IncomingSong) =
        ImportPlanner.planSongs(incoming.toList(), library.keys) { library[it] }

    @Test
    fun `a song written with short directives is the library's own`() = runTest {
        val library = "{title: River}\n{artist: Band}\n\n{start_of_chorus}\n[C]la\n{end_of_chorus}\n"
        val incoming = "{t:River}\n{artist:Band}\n\n{soc}\n[C]la\n{eoc}\n"

        val againstLibrary = ImportPlanner.planSongs(
            incoming = listOf(song(incoming, sourceFileName = "x.cho")),
            libraryFileNames = listOf("x.cho"),
            readLibraryText = mapOf("x.cho" to library).exact(),
        )
        val withinBatch = ImportPlanner.planSongs(
            incoming = listOf(song(library, sourceFileName = "a.cho"), song(incoming, sourceFileName = "b.cho")),
            libraryFileNames = emptyList(),
            readLibraryText = { null },
        )

        assertEquals(listOf(ImportPlan.Status.IDENTICAL), againstLibrary.map { it.status })
        assertEquals(ImportPlan.Status.IDENTICAL, withinBatch.last().status)
        assertEquals(0, withinBatch.last().repeatedEntryIndex)
    }

    /** A read that finds only the exact name, as a case-sensitive file system does. */
    private fun Map<String, String>.exact(): suspend (String) -> String? = { this[it] }

    /** A read that ignores case and Unicode form, as macOS does. */
    private fun Map<String, String>.folding(): suspend (String) -> String? = { name ->
        entries.firstOrNull { it.key.normalizedToNfc().equals(name.normalizedToNfc(), ignoreCase = true) }?.value
    }

    @Test
    fun `a setlist points at the song of its own archive where two arrived under one name`() = runTest {
        val songs = ImportPlanner.planSongs(
            incoming = listOf(
                ImportPlanner.IncomingSong(fileName = "a-song.cho", text = A, sourceFileName = "Song.cho", origin = 0),
                ImportPlanner.IncomingSong(fileName = "b-song.cho", text = B, sourceFileName = "Song.cho", origin = 1),
            ),
            libraryFileNames = emptyList(),
            readLibraryText = { null },
        )
        val named = SONG_SETLIST.copy(entries = listOf(Setlist.Entry(songFileName = "Song.cho")))
        val setlists = ImportPlanner.planSetlists(
            incoming = listOf(
                ImportPlanner.IncomingSetlist(named.copy(fileName = "one.setlist.json"), "one.setlist.json", origin = 0),
                ImportPlanner.IncomingSetlist(named.copy(fileName = "two.setlist.json"), "two.setlist.json", origin = 1),
                ImportPlanner.IncomingSetlist(named.copy(fileName = "loose.setlist.json"), "loose.setlist.json"),
            ),
            librarySetlists = emptyList(),
            songFileNames = ImportPlanner.plannedSongFileNames(songs),
        )

        assertEquals(listOf(0, 1, null), setlists.map { it.origin })
        val songFileNames = ImportPlanner.plannedSongFileNames(songs)
        assertEquals(
            listOf("a-song.cho", "b-song.cho"),
            setlists.take(2).map { entry -> entry.setlist.withSongFileNames(songFileNames.forOrigin(entry.origin)).entries.single().songFileName },
        )
        // A loose setlist still finds a song that arrived in an archive.
        val loose = ImportPlanner.SongFileNames(mapOf(0 to mapOf("Only.cho" to "only.cho"))).forOrigin(null)
        assertEquals("only.cho", named.copy(entries = listOf(Setlist.Entry(songFileName = "Only.cho"))).withSongFileNames(loose).entries.single().songFileName)
    }

    private fun song(text: String, sourceFileName: String? = null) =
        ImportPlanner.IncomingSong(fileName = "x.cho", text = text, sourceFileName = sourceFileName)

    private fun setlist(fileName: String, title: String) = Setlist(
        fileName = fileName,
        title = title,
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = emptyList(),
        size = 0L,
    )

    private companion object {
        val SONG_SETLIST = Setlist(
            fileName = "set.setlist.json",
            title = "Set",
            description = "",
            date = LocalDate(2026, 1, 1),
            isArchived = false,
            entries = listOf(Setlist.Entry(songFileName = "song.cho")),
            size = 0L,
        )
        const val A = "{title: A}\nA\n"
        const val B = "{title: B}\nB\n"
        const val C = "{title: C}\nC\n"
    }
}
