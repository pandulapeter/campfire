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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun aDifferentSongWantingANameTheBatchBringsBackUnchangedIsNew() = runTest {
        val plan = plan(
            library = mapOf("x.cho" to A),
            song(text = A, sourceFileName = "x.cho"),
            song(text = B, sourceFileName = "x_2.cho"),
        )

        assertEquals(listOf(ImportPlan.Status.IDENTICAL, ImportPlan.Status.NEW), plan.map { it.status })
        assertEquals(listOf("x.cho", "x.cho"), plan.map { it.fileName })
        assertFalse(ImportPlan(songs = plan).hasConflicts)
        assertEquals(mapOf("x.cho" to "x.cho", "x_2.cho" to "x.cho"), ImportPlanner.plannedSongFileNames(plan))
    }

    @Test
    fun aConflictCopyGivenBeforeTheLibrarysSongIsNew() = runTest {
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
    fun aDifferentSongIsNewWhicheverSideOfTheLibrarysSongItArrivesOn() = runTest {
        val libraryFirst = plan(library = mapOf("x.cho" to A), song(text = A), song(text = B))
        val libraryLast = plan(library = mapOf("x.cho" to A), song(text = B), song(text = A))

        assertEquals(listOf(ImportPlan.Status.IDENTICAL, ImportPlan.Status.NEW), libraryFirst.map { it.status })
        assertEquals(listOf(ImportPlan.Status.NEW, ImportPlan.Status.IDENTICAL), libraryLast.map { it.status })
        assertFalse(ImportPlan(songs = libraryFirst).hasConflicts)
        assertFalse(ImportPlan(songs = libraryLast).hasConflicts)
    }

    @Test
    fun repeatsAroundTheLibrarysSongFollowTheFirstOfThem() = runTest {
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
    fun twoDifferentSongsBeforeTheLibrarysSongAreBothNew() = runTest {
        val plan = plan(library = mapOf("x.cho" to A), song(text = B), song(text = C), song(text = A))

        assertEquals(listOf(ImportPlan.Status.NEW, ImportPlan.Status.NEW, ImportPlan.Status.IDENTICAL), plan.map { it.status })
        assertFalse(ImportPlan(songs = plan).hasConflicts)
    }

    @Test
    fun aDifferentSongIsStillAQuestionWhenTheBatchDoesNotBringTheLibrarysSong() = runTest {
        val plan = plan(library = mapOf("x.cho" to A), song(text = B))

        assertEquals(listOf(ImportPlan.Status.CONFLICTING), plan.map { it.status })
    }

    @Test
    fun aNumberedSiblingTheBatchBringsBackProtectsOnlyItself() = runTest {
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
    fun aSongWantingTheNameALibrarySongArrivedUnderIsNewInEitherOrder() = runTest {
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
    fun aDifferentSetlistWantingANameTheBatchBringsBackUnchangedIsNew() {
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
    fun aNumberedSetlistSiblingTheBatchBringsBackProtectsOnlyItself() {
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
    fun aSetlistWantingTheNameALibrarySetlistArrivedUnderIsNew() {
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
    fun aSetlistThatStopsBeingTheLibrarysOnReplanIsNeverReplaced() = runTest {
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
    fun aConflictingSetlistTheReplanFindsBroughtBackIsNoLongerAQuestion() = runTest {
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
    fun anIdenticalSongIsRecordedUnderTheSpellingTheLibraryLists() = runTest {
        val library = mapOf("Wonderwall.cho" to A)
        val incoming = listOf(ImportPlanner.IncomingSong(fileName = "wonderwall.cho", text = A, sourceFileName = "Wonderwall.cho"))

        listOf(library.folding(), library.exact()).forEach { read ->
            val plan = ImportPlanner.planSongs(incoming, library.keys, read)

            assertEquals(listOf(ImportPlan.Status.IDENTICAL), plan.map { it.status })
            assertEquals(listOf("Wonderwall.cho"), plan.map { it.fileName })
            assertEquals(mapOf("Wonderwall.cho" to "Wonderwall.cho"), ImportPlanner.plannedSongFileNames(plan))
        }
    }

    @Test
    fun aDifferentSongReplacesTheSpellingTheLibraryListsWhereTheFileSystemFoldsCase() = runTest {
        val library = mapOf("Wonderwall.cho" to A)
        val incoming = listOf(ImportPlanner.IncomingSong(fileName = "wonderwall.cho", text = B, sourceFileName = null))

        val plan = ImportPlanner.planSongs(incoming, library.keys, library.folding())

        assertEquals(listOf(ImportPlan.Status.CONFLICTING), plan.map { it.status })
        assertEquals("wonderwall.cho", plan.single().fileName)
        assertEquals("Wonderwall.cho", plan.single().replacesFileName)
        assertEquals(listOf("Wonderwall.cho"), ImportPlan(songs = plan).summary.conflictingFileNames)
    }

    @Test
    fun aDifferentSongIsANameOfItsOwnWhereTheFileSystemTellsCaseApart() = runTest {
        val library = mapOf("Wonderwall.cho" to A)
        val incoming = listOf(ImportPlanner.IncomingSong(fileName = "wonderwall.cho", text = B, sourceFileName = null))

        val plan = ImportPlanner.planSongs(incoming, library.keys, library.exact())

        assertEquals(listOf(ImportPlan.Status.NEW), plan.map { it.status })
        assertNull(plan.single().replacesFileName)
    }

    @Test
    fun aSongListedInDecomposedFormIsAMemberOfItsComposedFamily() = runTest {
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
    fun aDifferentSongNextToTheLibrarysUnderAnotherSpellingIsNew() = runTest {
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
    fun aSetlistFollowsItsSongToTheSpellingTheLibraryLists() = runTest {
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

    /** A read that finds only the exact name, as a case-sensitive file system does. */
    private fun Map<String, String>.exact(): suspend (String) -> String? = { this[it] }

    /** A read that ignores case and Unicode form, as macOS does. */
    private fun Map<String, String>.folding(): suspend (String) -> String? = { name ->
        entries.firstOrNull { it.key.normalizedToNfc().equals(name.normalizedToNfc(), ignoreCase = true) }?.value
    }

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
