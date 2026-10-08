/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.ParsedSetlist
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.ExtractedDocument
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.DocumentRepository
import com.pandulapeter.campfire.domain.implementation.ImportPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * Applying a plan is the one place anything in the library is overwritten, so what it replaces is pinned here as well
 * as in the planner: never a song the same import brings back unchanged.
 */
class ImportFilesUseCaseImplTest {

    @Test
    fun `ChordPro text and archives are prettified before they are written`() = runTest {
        val raw = "{tag: Folk}\r\n{artist: Singer}\r\n{title: Song}\r\n{soc}\r\n[G]Sing\r\n{eoc}\r\n{sov}\r\n[C]Words\r\n{eov}"
        val expected = "{title: Song}\n{artist: Singer}\n{tag: Folk}\n\n{soc}\n[G]Sing\n{eoc}\n\n{sov}\n[C]Words\n{eov}\n"
        val archive = FakeArchiveRepository { _, _ -> listOf(ImportedFile("source.cho", raw.encodeToByteArray())) }
        for (source in listOf("source.cho", "source.txt", "source.zip")) {
            val songs = FakeSongRepository(mutableMapOf())
            val plan = prepare(songs, archive = archive)(listOf(ImportedFile(source, raw.encodeToByteArray())))
            assertEquals(expected, plan.songs.single().text)
            assertFalse(plan.songs.single().isConverted)
            ImportFilesUseCaseImpl(songs, FakeSetlistRepository(), Logger.Standard).invoke(plan, ImportConflictResolution.KEEP_BOTH)
            assertEquals(expected, songs.files["song.cho"])
        }
    }

    @Test
    fun `formatted imports match older library files and repeated batch entries`() = runTest {
        val raw = "{artist: Singer}\n{title: Song}\n{soc}\n[G]Sing\n{eoc}\n{sov}\n[C]Words\n{eov}"
        val formatted = "{title: Song}\n{artist: Singer}\n\n{soc}\n[G]Sing\n{eoc}\n\n{sov}\n[C]Words\n{eov}\n"
        val files = listOf(ImportedFile("source.cho", raw.encodeToByteArray()), ImportedFile("copy.cho", formatted.encodeToByteArray()))
        val existing = prepare(FakeSongRepository(mutableMapOf("song.cho" to raw)))(files)
        assertTrue(existing.songs.all { it.status == ImportPlan.Status.IDENTICAL })
        assertFalse(existing.hasConflicts)
        val batch = prepare(FakeSongRepository(mutableMapOf()))(files)
        assertEquals(listOf(ImportPlan.Status.NEW, ImportPlan.Status.IDENTICAL), batch.songs.map { it.status })
    }

    @Test
    fun `converted documents are prettified too`() = runTest {
        val source = object : DocumentRepository {
            override suspend fun extract(file: ImportedFile) = ExtractedDocument(listOf(ExtractedDocument.Page(listOf(
                ExtractedDocument.Line(listOf(ExtractedDocument.Span("Song", 0.0, 80.0, 20.0)), isHeading = true),
                ExtractedDocument.Line(listOf(ExtractedDocument.Span("Am", 0.0, 12.0, 10.0), ExtractedDocument.Span("C", 42.0, 48.0, 10.0))),
                ExtractedDocument.Line(listOf(ExtractedDocument.Span("Hello world", 0.0, 66.0, 10.0))),
            ))))
        }
        for (name in listOf("source.pdf", "source.docx")) {
            val plan = prepare(FakeSongRepository(mutableMapOf()), source)(listOf(ImportedFile(name, byteArrayOf(1))))
            assertEquals("{title: Song}\n\n[Am]Hello [C]world\n", plan.songs.single().text)
            assertTrue(plan.songs.single().isConverted)
        }
    }

    @Test
    fun `text conversion joins the ordinary planner and a second import is a duplicate`() = runTest {
        val songs = FakeSongRepository(mutableMapOf())
        val prepare = prepare(songs)
        val plain = ImportedFile("sheet.txt", "Am     C\nHello world".encodeToByteArray())
        val plan = prepare(listOf(plain))
        assertEquals("[Am]Hello [C]world\n", plan.songs.single().text)
        assertTrue(plan.songs.single().isConverted)
        val result = ImportFilesUseCaseImpl(songs, FakeSetlistRepository(), Logger.Standard).invoke(plan, ImportConflictResolution.KEEP_BOTH)
        assertEquals(listOf("sheet.cho"), result.convertedSongFileNames)
        assertEquals("sheet.cho", result.convertedSongToOpen)
        assertEquals(ImportPlan.Status.IDENTICAL, prepare(listOf(plain)).songs.single().status)
        val chordPro = ImportedFile("sheet.cho", "Am     C\nHello world".encodeToByteArray())
        assertEquals("Am     C\nHello world\n", prepare(listOf(chordPro)).songs.single().text)
        assertFalse(prepare(listOf(chordPro)).songs.single().isConverted)
        val collection = "{title: A}\r\n[A]hello\r\n{new_song}\r\n{title: B}\r\n[B]hello\r\n"
        assertTrue(prepare(listOf(ImportedFile("backup.txt", collection.encodeToByteArray()))).songs.all { !it.isConverted })
    }

    @Test
    fun `documents use headers preserve source names and report unreadable ones separately`() = runTest {
        val songs = FakeSongRepository(mutableMapOf())
        val document = ExtractedDocument(listOf(ExtractedDocument.Page(listOf(
            ExtractedDocument.Line(listOf(ExtractedDocument.Span("Title", 0.0, 80.0, 20.0)), isHeading = true),
            ExtractedDocument.Line(listOf(ExtractedDocument.Span("Am", 0.0, 12.0, 10.0), ExtractedDocument.Span("C", 42.0, 48.0, 10.0))),
            ExtractedDocument.Line(listOf(ExtractedDocument.Span("Hello world", 0.0, 66.0, 10.0))),
        ))))
        val source = object : DocumentRepository {
            override suspend fun extract(file: ImportedFile) = document.takeIf { file.bytes.isNotEmpty() }
        }
        val files = listOf(ImportedFile("original.PDF", byteArrayOf(1)), ImportedFile.unread("scan.pdf"), ImportedFile.unread("old.doc"))
        val plan = prepare(songs, source)(files)
        assertEquals("title.cho", plan.songs.single().fileName)
        assertEquals("original.PDF", plan.songs.single().sourceFileName)
        assertEquals(listOf("scan.pdf", "old.doc"), plan.unreadableDocumentFileNames)
        assertTrue(plan.skippedFileNames.isEmpty())
        assertEquals(2, plan.summary.unreadableDocumentCount)
        val result = ImportFilesUseCaseImpl(songs, FakeSetlistRepository(), Logger.Standard).invoke(plan, ImportConflictResolution.KEEP_BOTH)
        assertEquals(listOf("title.cho"), result.convertedSongFileNames)
        assertEquals(plan.unreadableDocumentFileNames, result.unreadableDocumentFileNames)
        assertNull(result.convertedSongToOpen)
        assertEquals(listOf("oversized.pdf"), prepare(songs, source)(listOf(ImportedFile.unread("oversized.pdf", isTooLarge = true))).oversizedFileNames)
    }

    @Test
    fun `archive documents and text take the same conversion path and skipped conflicts are not counted as converted`() = runTest {
        val songs = FakeSongRepository(mutableMapOf("sheet.cho" to "old text\n"))
        val archive = FakeArchiveRepository { _, maxSize ->
            assertEquals(ImportLimits.MAX_IMPORT_SIZE, maxSize)
            listOf(ImportedFile("sheet.txt", "Am C\nNew words".encodeToByteArray()), ImportedFile.unread("scan.docx"))
        }
        val plan = prepare(songs, archive = archive)(listOf(ImportedFile("archive.zip", byteArrayOf(1))))
        assertTrue(plan.hasConflicts)
        assertEquals(listOf("scan.docx"), plan.unreadableDocumentFileNames)
        val result = ImportFilesUseCaseImpl(songs, FakeSetlistRepository(), Logger.Standard).invoke(plan, ImportConflictResolution.SKIP)
        assertTrue(result.convertedSongFileNames.isEmpty())
        assertNull(result.convertedSongToOpen)
    }

    @Test
    fun `a file of an unknown type is unpacked by its content, and a document is never`() = runTest {
        val unpacked = mutableListOf<Int>()
        val archive = FakeArchiveRepository { bytes, _ ->
            unpacked += bytes.size
            listOf(ImportedFile("a.cho", "{title: A}".encodeToByteArray()))
        }
        val zip = byteArrayOf(0x50, 0x4B, 3, 4, 0)
        val plan = prepare(FakeSongRepository(mutableMapOf()), archive = archive)(
            listOf(ImportedFile("library.backup", zip), ImportedFile("set.sbp", byteArrayOf(1, 2)), ImportedFile("notes.bin", byteArrayOf(1))),
        )
        assertEquals(listOf(5, 2), unpacked)
        assertEquals(listOf("notes.bin"), plan.skippedFileNames)
        assertEquals(listOf("a.cho", "a.cho"), plan.songs.map { it.sourceFileName })
        // A Word document is a zip archive too, and is read as the document it says it is.
        prepare(FakeSongRepository(mutableMapOf()), archive = archive)(listOf(ImportedFile("sheet.docx", zip)))
        assertEquals(listOf(5, 2), unpacked)
    }

    @Test
    fun `a file of an unknown type that holds nothing an import reads is reported under its own name`() = runTest {
        var entries = emptyList<ImportedFile>()
        val archive = FakeArchiveRepository { _, _ -> entries }
        val zip = byteArrayOf(0x50, 0x4B, 3, 4, 0)
        val prepare = prepare(FakeSongRepository(mutableMapOf()), archive = archive)
        val document = listOf(ImportedFile.unread("mimetype"), ImportedFile("content.xml", "<office/>".encodeToByteArray()), ImportedFile.unread("thumbnail.png"))

        entries = document
        val plan = prepare(listOf(ImportedFile("sheet.odt", zip)))
        assertEquals(listOf("sheet.odt"), plan.skippedFileNames)
        assertTrue(plan.songs.isEmpty())
        entries = listOf(ImportedFile.unread("dataFile.txt"))
        assertEquals(listOf("library.backup"), prepare(listOf(ImportedFile("library.backup", zip))).skippedFileNames)
        entries = emptyList()
        assertEquals(listOf("empty.backup"), prepare(listOf(ImportedFile("empty.backup", zip))).skippedFileNames)
        // A file picked as an archive by its name is reported by what it holds, as it always was.
        entries = document
        assertEquals(listOf("mimetype", "content.xml", "thumbnail.png"), prepare(listOf(ImportedFile("songs.zip", zip))).skippedFileNames)
    }

    @Test
    fun `converted replacements are counted and already imported conversions are not`() = runTest {
        val songs = FakeSongRepository(mutableMapOf("sheet.cho" to "old words\n"))
        val plain = ImportedFile("sheet.txt", "Am     C\nHello world".encodeToByteArray())
        val prepare = prepare(songs)
        val result = ImportFilesUseCaseImpl(songs, FakeSetlistRepository(), Logger.Standard).invoke(prepare(listOf(plain)), ImportConflictResolution.REPLACE)
        assertEquals(listOf("sheet.cho"), result.convertedSongFileNames)
        assertEquals("sheet.cho", result.convertedSongToOpen)
        assertEquals("[Am]Hello [C]world\n", songs.files["sheet.cho"])
        val repeated = ImportFilesUseCaseImpl(songs, FakeSetlistRepository(), Logger.Standard).invoke(prepare(listOf(plain)), ImportConflictResolution.REPLACE)
        assertEquals(listOf("sheet.cho"), repeated.duplicateFileNames)
        assertTrue(repeated.convertedSongFileNames.isEmpty())
        assertNull(repeated.convertedSongToOpen)
    }

    @Test
    fun `multi song documents name each header and offer no single song open action`() = runTest {
        val songs = FakeSongRepository(mutableMapOf())
        val documents = object : DocumentRepository {
            override suspend fun extract(file: ImportedFile) = ExtractedDocument(listOf("First", "Second").map { title ->
                ExtractedDocument.Page(listOf(
                    ExtractedDocument.Line(listOf(ExtractedDocument.Span(title, 0.0, 80.0, 20.0)), isHeading = true),
                    ExtractedDocument.Line(listOf(ExtractedDocument.Span("Am", 0.0, 12.0, 10.0), ExtractedDocument.Span("C", 42.0, 48.0, 10.0))),
                    ExtractedDocument.Line(listOf(ExtractedDocument.Span("Hello world", 0.0, 66.0, 10.0))),
                ))
            })
        }
        val plan = prepare(songs, documents)(listOf(ImportedFile("songbook.pdf", byteArrayOf(1))))
        assertEquals(listOf("first.cho", "second.cho"), plan.songs.map { it.fileName })
        assertTrue(plan.songs.all { it.sourceFileName == null && it.isConverted })
        val result = ImportFilesUseCaseImpl(songs, FakeSetlistRepository(), Logger.Standard).invoke(plan, ImportConflictResolution.KEEP_BOTH)
        assertEquals(listOf("first.cho", "second.cho"), result.convertedSongFileNames)
        assertNull(result.convertedSongToOpen)
    }

    private fun prepare(
        songs: FakeSongRepository,
        documents: DocumentRepository = object : DocumentRepository {
            override suspend fun extract(file: ImportedFile): ExtractedDocument? = null
        },
        archive: ArchiveRepository = FakeArchiveRepository(),
        setlists: FakeSetlistRepository = FakeSetlistRepository(),
    ) = PrepareImportUseCaseImpl(
        archiveRepository = archive,
        songRepository = songs,
        songContentRepository = object : SongContentRepositoryStub() {
            override suspend fun loadSongContent(fileName: String, useCache: Boolean) = songs.files[fileName]?.let { SongContent(fileName, it) }
        },
        setlistRepository = setlists,
        documentRepository = documents,
        logger = Logger.Standard,
    )

    @Test
    fun `replace never overwrites a song the import brings back`() = runTest {
        ImportConflictResolution.entries.forEach { resolution ->
            val songs = FakeSongRepository(files = mutableMapOf("foo.cho" to A))
            val setlists = FakeSetlistRepository()
            val songEntries = ImportPlanner.planSongs(
                incoming = listOf(
                    ImportPlanner.IncomingSong(fileName = "foo.cho", text = A, sourceFileName = "foo.cho"),
                    ImportPlanner.IncomingSong(fileName = "foo.cho", text = B, sourceFileName = "foo_2.cho"),
                ),
                libraryFileNames = songs.files.keys.toList(),
                readLibraryText = songs.files::get,
            )
            val setlistEntries = ImportPlanner.planSetlists(
                incoming = listOf(ImportPlanner.IncomingSetlist(setlist(entries = listOf("foo.cho", "foo_2.cho")), "set.setlist.json")),
                librarySetlists = emptyList(),
                songFileNames = ImportPlanner.plannedSongFileNames(songEntries),
            )
            val plan = ImportPlan(songs = songEntries, setlists = setlistEntries)
            assertFalse(plan.hasConflicts)

            ImportFilesUseCaseImpl(songRepository = songs, setlistRepository = setlists, logger = Logger.Standard).invoke(plan, resolution)

            assertEquals(mapOf("foo.cho" to A, "foo_2.cho" to B), songs.files, "$resolution")
            assertEquals(listOf("foo.cho", "foo_2.cho"), setlists.files.values.single().entries.map { it.songFileName }, "$resolution")
        }
    }

    @Test
    fun `a setlist is written pointing at the song of its own archive where two arrived under one name`() = runTest {
        val songs = FakeSongRepository(files = mutableMapOf())
        val setlists = FakeSetlistRepository()
        val songEntries = ImportPlanner.planSongs(
            incoming = listOf(
                ImportPlanner.IncomingSong(fileName = "a-song.cho", text = A, sourceFileName = "Song.cho", origin = 0),
                ImportPlanner.IncomingSong(fileName = "b-song.cho", text = B, sourceFileName = "Song.cho", origin = 1),
            ),
            libraryFileNames = emptyList(),
            readLibraryText = { null },
        )
        val setlistEntries = ImportPlanner.planSetlists(
            incoming = listOf("one", "two").mapIndexed { origin, name ->
                ImportPlanner.IncomingSetlist(setlist(entries = listOf("Song.cho")).copy(fileName = "$name.setlist.json", title = name), "$name.setlist.json", origin)
            },
            librarySetlists = emptyList(),
            songFileNames = ImportPlanner.plannedSongFileNames(songEntries),
        )

        ImportFilesUseCaseImpl(songRepository = songs, setlistRepository = setlists, logger = Logger.Standard).invoke(ImportPlan(songs = songEntries, setlists = setlistEntries), ImportConflictResolution.KEEP_BOTH)

        assertEquals(
            mapOf("one" to listOf("a-song.cho"), "two" to listOf("b-song.cho")),
            setlists.files.values.associate { setlist -> setlist.title to setlist.entries.map { it.songFileName } },
        )
    }

    @Test
    fun `a repeat of a skipped conflicting song is skipped with it rather than a duplicate of the library song`() = runTest {
        fun plan() = ImportPlan(
            songs = listOf(
                ImportPlan.SongEntry(fileName = "x.cho", text = B, status = ImportPlan.Status.CONFLICTING, sourceFileName = "first.cho"),
                ImportPlan.SongEntry(
                    fileName = "x.cho",
                    text = B,
                    status = ImportPlan.Status.IDENTICAL,
                    sourceFileName = "second.cho",
                    repeatedEntryIndex = 0,
                ),
            ),
        )
        val skipped = FakeSongRepository(files = mutableMapOf("x.cho" to A))
        val skippedResult = ImportFilesUseCaseImpl(skipped, FakeSetlistRepository(), Logger.Standard).invoke(plan(), ImportConflictResolution.SKIP)
        assertEquals(listOf("x.cho"), skippedResult.skippedConflictingFileNames)
        assertTrue(skippedResult.duplicateFileNames.isEmpty())
        assertEquals(mapOf("x.cho" to A), skipped.files)

        val kept = FakeSongRepository(files = mutableMapOf("x.cho" to A))
        val keptResult = ImportFilesUseCaseImpl(kept, FakeSetlistRepository(), Logger.Standard).invoke(plan(), ImportConflictResolution.KEEP_BOTH)
        assertEquals(listOf("x_2.cho"), keptResult.importedSongFileNames)
        assertTrue(keptResult.duplicateFileNames.isEmpty())
        assertTrue(keptResult.skippedConflictingFileNames.isEmpty())
    }

    @Test
    fun `a song the batch brings twice is listed once as already in the library`() = runTest {
        val songs = FakeSongRepository(files = mutableMapOf("x.cho" to A))
        val plan = ImportPlan(
            songs = listOf(
                ImportPlan.SongEntry(fileName = "x.cho", text = A, status = ImportPlan.Status.IDENTICAL, sourceFileName = "a/x.cho"),
                ImportPlan.SongEntry(fileName = "x.cho", text = A, status = ImportPlan.Status.IDENTICAL, sourceFileName = "b/x.cho"),
            ),
        )

        val result = ImportFilesUseCaseImpl(songs, FakeSetlistRepository(), Logger.Standard).invoke(plan, ImportConflictResolution.KEEP_BOTH)

        assertEquals(listOf("x.cho"), result.duplicateFileNames)
    }

    @Test
    fun `a setlist the batch brings twice is listed once as already in the library`() = runTest {
        val setlists = FakeSetlistRepository().apply { files["set.setlist.json"] = setlist(emptyList()) }
        val plan = ImportPlan(
            setlists = listOf(
                ImportPlan.SetlistEntry("set.setlist.json", setlist(emptyList()), ImportPlan.Status.IDENTICAL, "a/set.setlist.json"),
                ImportPlan.SetlistEntry("set.setlist.json", setlist(emptyList()), ImportPlan.Status.IDENTICAL, "b/set.setlist.json"),
            ),
        )

        val result = ImportFilesUseCaseImpl(FakeSongRepository(mutableMapOf()), setlists, Logger.Standard).invoke(plan, ImportConflictResolution.KEEP_BOTH)

        assertEquals(listOf("set.setlist.json"), result.duplicateFileNames)
        assertEquals(listOf("set.setlist.json"), setlists.files.keys.toList())
    }

    @Test
    fun `a skipped conflict points the batch's setlists at the library's spelling`() = runTest {
        val songs = FakeSongRepository(files = mutableMapOf("Beatles-Yesterday.cho" to A))
        val setlists = FakeSetlistRepository()
        val plan = ImportPlan(
            songs = listOf(
                ImportPlan.SongEntry(
                    fileName = "beatles-yesterday.cho",
                    text = B,
                    status = ImportPlan.Status.CONFLICTING,
                    sourceFileName = "beatles-yesterday.cho",
                    replacesFileName = "Beatles-Yesterday.cho",
                ),
            ),
            setlists = listOf(
                ImportPlan.SetlistEntry("set.setlist.json", setlist(listOf("beatles-yesterday.cho")), ImportPlan.Status.NEW, "set.setlist.json"),
            ),
        )

        val result = ImportFilesUseCaseImpl(songs, setlists, Logger.Standard).invoke(plan, ImportConflictResolution.SKIP)

        assertEquals(listOf("Beatles-Yesterday.cho"), setlists.files.values.single().entries.map { it.songFileName })
        assertEquals(listOf("Beatles-Yesterday.cho"), result.skippedConflictingFileNames)
        assertEquals(mapOf("Beatles-Yesterday.cho" to A), songs.files)
    }

    @Test
    fun `an identical song whose library file is gone by the answer is written under its own name`() = runTest {
        val text = "{title: Song}\n"
        fun plan() = ImportPlan(
            songs = listOf(ImportPlan.SongEntry(fileName = "song_2.cho", text = text, status = ImportPlan.Status.IDENTICAL, sourceFileName = "song_2.cho")),
        )
        val gone = FakeSongRepository(files = mutableMapOf())
        val goneResult = ImportFilesUseCaseImpl(gone, FakeSetlistRepository(), Logger.Standard).invoke(plan(), ImportConflictResolution.SKIP)
        assertEquals(listOf("song.cho" to false), gone.importCalls)
        assertEquals(listOf("song.cho"), goneResult.importedSongFileNames)
        assertTrue(goneResult.duplicateFileNames.isEmpty())

        for (libraryName in listOf("song_2.cho", "Song_2.cho")) {
            val there = FakeSongRepository(files = mutableMapOf(libraryName to text))
            val thereResult = ImportFilesUseCaseImpl(there, FakeSetlistRepository(), Logger.Standard).invoke(plan(), ImportConflictResolution.SKIP)
            assertTrue(there.importCalls.isEmpty(), libraryName)
            assertEquals(listOf("song_2.cho"), thereResult.duplicateFileNames, libraryName)
        }
    }

    @Test
    fun `the applier refuses to replace a kept name even if the plan asks`() = runTest {
        val songs = FakeSongRepository(files = mutableMapOf("foo.cho" to A))
        val plan = ImportPlan(
            songs = listOf(
                ImportPlan.SongEntry(fileName = "foo.cho", text = A, status = ImportPlan.Status.IDENTICAL, sourceFileName = "foo.cho"),
                ImportPlan.SongEntry(fileName = "foo.cho", text = B, status = ImportPlan.Status.CONFLICTING, sourceFileName = "foo_2.cho"),
            ),
        )

        ImportFilesUseCaseImpl(songRepository = songs, setlistRepository = FakeSetlistRepository(), logger = Logger.Standard).invoke(plan, ImportConflictResolution.REPLACE)

        assertEquals(mapOf("foo.cho" to A, "foo_2.cho" to B), songs.files)
    }

    @Test
    fun `a replacement goes over the spelling the library lists and keeping both under the derived name`() = runTest {
        val plan = ImportPlan(
            songs = listOf(
                ImportPlan.SongEntry(
                    fileName = "wonderwall.cho",
                    text = B,
                    status = ImportPlan.Status.CONFLICTING,
                    sourceFileName = null,
                    replacesFileName = "Wonderwall.cho",
                ),
            ),
        )
        val expectedCalls = mapOf(
            ImportConflictResolution.REPLACE to ("Wonderwall.cho" to true),
            ImportConflictResolution.KEEP_BOTH to ("wonderwall.cho" to false),
        )

        expectedCalls.forEach { (resolution, expectedCall) ->
            val songs = FakeSongRepository(files = mutableMapOf("Wonderwall.cho" to A))

            ImportFilesUseCaseImpl(songRepository = songs, setlistRepository = FakeSetlistRepository(), logger = Logger.Standard).invoke(plan, resolution)

            assertEquals(listOf(expectedCall), songs.importCalls, "$resolution")
        }
    }

    @Test
    fun `an import puts what it wrote into the lists without rescanning them`() = runTest {
        val songs = FakeSongRepository(files = mutableMapOf("foo.cho" to A))
        val setlists = FakeSetlistRepository()
        val plan = ImportPlan(
            songs = listOf(
                ImportPlan.SongEntry(fileName = "foo.cho", text = B, status = ImportPlan.Status.CONFLICTING, sourceFileName = null),
                ImportPlan.SongEntry(fileName = "bar.cho", text = A, status = ImportPlan.Status.NEW, sourceFileName = null),
            ),
            setlists = ImportPlanner.planSetlists(
                incoming = listOf(ImportPlanner.IncomingSetlist(setlist(entries = listOf("bar.cho")), "set.setlist.json")),
                librarySetlists = emptyList(),
                songFileNames = mapOf("bar.cho" to "bar.cho"),
            ),
        )

        ImportFilesUseCaseImpl(songRepository = songs, setlistRepository = setlists, logger = Logger.Standard).invoke(plan, ImportConflictResolution.REPLACE)

        assertEquals(listOf("foo.cho", "bar.cho"), songs.adopted)
        assertEquals(listOf("set.setlist.json"), setlists.adopted)
        assertEquals(0, songs.rescanCount)
    }

    @Test
    fun `an undated setlist that replaces a library one keeps its date and one kept next to it keeps the import's`() = runTest {
        val planned = LocalDate(2026, 1, 10)
        // What parsing gives a file that names no day: the day it is imported on.
        val today = LocalDate(2026, 10, 6)
        val library = setlist(entries = listOf("a.cho")).copy(fileName = "gig.setlist.json", title = "Gig", date = planned)
        fun incoming(vararg entries: List<String>) = entries.map {
            ImportPlanner.IncomingSetlist(
                setlist = library.copy(date = today, entries = it.map(Setlist::Entry)),
                sourceFileName = library.fileName,
                isDated = false,
            )
        }
        suspend fun importing(incoming: List<ImportPlanner.IncomingSetlist>, resolution: ImportConflictResolution) = FakeSetlistRepository().also { setlists ->
            setlists.files[library.fileName] = library
            val plan = ImportPlan(
                setlists = ImportPlanner.planSetlists(incoming = incoming, librarySetlists = listOf(library), songFileNames = emptyMap()),
            )
            ImportFilesUseCaseImpl(songRepository = FakeSongRepository(files = mutableMapOf()), setlistRepository = setlists, logger = Logger.Standard).invoke(plan, resolution)
        }

        assertEquals(planned, importing(incoming(listOf("b.cho")), ImportConflictResolution.REPLACE).files.getValue("gig.setlist.json").date)
        assertEquals(today, importing(incoming(listOf("b.cho")), ImportConflictResolution.KEEP_BOTH).files.getValue("gig_2.setlist.json").date)
        importing(incoming(listOf("b.cho"), listOf("c.cho")), ImportConflictResolution.REPLACE).files.let { files ->
            assertEquals(listOf(Setlist.Entry("b.cho")), files.getValue("gig.setlist.json").entries)
            assertEquals(planned, files.getValue("gig.setlist.json").date)
            assertEquals(listOf(Setlist.Entry("c.cho")), files.getValue("gig_2.setlist.json").entries)
            assertEquals(today, files.getValue("gig_2.setlist.json").date)
        }
    }

    @Test
    fun `an undated setlist that replaces a library one keeps its countdown and a dated one brings its own`() = runTest {
        val library = setlist(entries = listOf("a.cho")).copy(
            fileName = "gig.setlist.json",
            title = "Gig",
            date = LocalDate(2026, 1, 10),
            isCountdownShown = true,
        )
        suspend fun replacing(incoming: Setlist, isDated: Boolean = true) = FakeSetlistRepository().let { setlists ->
            setlists.files[library.fileName] = library
            val plan = ImportPlan(
                setlists = ImportPlanner.planSetlists(
                    incoming = listOf(ImportPlanner.IncomingSetlist(setlist = incoming, sourceFileName = library.fileName, isDated = isDated)),
                    librarySetlists = listOf(library),
                    songFileNames = emptyMap(),
                ),
            )
            assertEquals(listOf(ImportPlan.Status.CONFLICTING), plan.setlists.map { it.status })
            ImportFilesUseCaseImpl(songRepository = FakeSongRepository(files = mutableMapOf()), setlistRepository = setlists, logger = Logger.Standard)
                .invoke(plan, ImportConflictResolution.REPLACE)
            setlists.files.getValue(library.fileName)
        }

        replacing(library.copy(date = LocalDate(2026, 10, 6), isCountdownShown = false, description = "Changed"), isDated = false).let { written ->
            assertEquals("Changed", written.description)
            assertEquals(library.date, written.date)
            assertEquals(true, written.isCountdownShown)
        }
        assertEquals(false, replacing(library.copy(isCountdownShown = false)).isCountdownShown)
    }

    @Test
    fun `an import that fails halfway still puts what it wrote into the list`() = runTest {
        val songs = FakeSongRepository(files = mutableMapOf(), failingImport = 2)
        val plan = ImportPlan(
            songs = listOf("one.cho", "two.cho", "three.cho").map {
                ImportPlan.SongEntry(fileName = it, text = A, status = ImportPlan.Status.NEW, sourceFileName = null)
            },
        )

        val result = ImportFilesUseCaseImpl(songRepository = songs, setlistRepository = FakeSetlistRepository(), logger = Logger.Standard)
            .invoke(plan, ImportConflictResolution.KEEP_BOTH)

        assertTrue(result.isFailed)
        assertEquals(listOf("one.cho", "two.cho"), result.importedSongFileNames)
        assertEquals(listOf("three.cho"), result.failedFileNames)
        assertTrue(result.unprocessedFileNames.isEmpty())
        assertEquals(listOf("one.cho", "two.cho"), songs.adopted)
        assertEquals(0, songs.rescanCount)
    }

    @Test
    fun `a stopped bulk import reports the failed source and every unprocessed entry`() = runTest {
        val songs = FakeSongRepository(mutableMapOf(), failingImport = 1)
        val setlists = FakeSetlistRepository()
        val plan = ImportPlan(
            songs = (1..4).map {
                ImportPlan.SongEntry("$it.cho", A, ImportPlan.Status.NEW, "source$it.txt", isConverted = true)
            },
            setlists = listOf(ImportPlan.SetlistEntry("set.setlist.json", setlist(listOf("source1.txt")), ImportPlan.Status.NEW, "source.json")),
        )
        val result = ImportFilesUseCaseImpl(songs, setlists, Logger.Standard).invoke(plan, ImportConflictResolution.KEEP_BOTH)
        assertTrue(result.isFailed)
        assertEquals(listOf("1.cho"), result.importedSongFileNames)
        assertEquals(listOf("1.cho"), result.convertedSongFileNames)
        assertEquals(listOf("source2.txt"), result.failedFileNames)
        assertEquals(listOf("source3.txt", "source4.txt", "source.json"), result.unprocessedFileNames)
        assertTrue(setlists.files.isEmpty())
        assertNull(result.convertedSongToOpen)
    }

    @Test
    fun `progress counts duplicates and skipped conflicts without counting them as imported`() = runTest {
        val songs = FakeSongRepository(mutableMapOf("duplicate.cho" to A, "conflict.cho" to B))
        val events = mutableListOf<ImportProgress>()
        val plan = ImportPlan(songs = listOf(
            ImportPlan.SongEntry("new.cho", A, ImportPlan.Status.NEW, null),
            ImportPlan.SongEntry("duplicate.cho", A, ImportPlan.Status.IDENTICAL, null),
            ImportPlan.SongEntry("conflict.cho", A, ImportPlan.Status.CONFLICTING, null),
        ))
        val result = ImportFilesUseCaseImpl(songs, FakeSetlistRepository(), Logger.Standard).invoke(plan, ImportConflictResolution.SKIP, onProgress = events::add)
        val importing = events.filter { it.phase == ImportProgress.Phase.IMPORTING }
        assertEquals(listOf(0, 1, 2, 3), importing.map { it.completed })
        assertTrue(importing.all { it.total == 3 })
        assertEquals(ImportProgress.Phase.FINISHING, events.last().phase)
        assertEquals(listOf("new.cho"), result.importedSongFileNames)
        assertEquals(listOf("duplicate.cho"), result.duplicateFileNames)
        assertEquals(listOf("conflict.cho"), result.skippedConflictingFileNames)
        assertTrue(result.skippedFileNames.isEmpty())
        assertFalse(result.isFailed)
    }

    @Test
    fun `preparation reports expanded archive files and compares before writing`() = runTest {
        val songs = FakeSongRepository(mutableMapOf())
        val events = mutableListOf<ImportProgress>()
        val archive = FakeArchiveRepository { _, _ ->
            listOf(ImportedFile("one.cho", A.encodeToByteArray()), ImportedFile("two.cho", B.encodeToByteArray()))
        }
        val plan = prepare(songs, archive = archive).invoke(listOf(ImportedFile("bulk.zip", byteArrayOf(1))), events::add)
        assertEquals(2, plan.songs.size)
        assertEquals("bulk.zip", events.first().fileName)
        assertEquals(1, events.first().total)
        val reading = events.filter { it.phase == ImportProgress.Phase.READING }
        assertEquals(listOf(0, 1, 2), reading.map { it.completed })
        assertTrue(reading.all { it.total == 2 })
        assertEquals(ImportProgress.Phase.COMPARING, events.last().phase)
        assertTrue(songs.importCalls.isEmpty())
    }

    @Test
    fun `a setlist failure reports completed songs and setlists without attempting the remainder`() = runTest {
        val songs = FakeSongRepository(mutableMapOf())
        val setlists = FakeSetlistRepository(failingImport = 1)
        val plan = ImportPlan(
            songs = listOf(ImportPlan.SongEntry("song.cho", A, ImportPlan.Status.NEW, "source.cho")),
            setlists = (1..3).map { index ->
                val incoming = setlist(listOf("source.cho")).copy(fileName = "set$index.setlist.json", title = "Set $index")
                ImportPlan.SetlistEntry(incoming.fileName, incoming, ImportPlan.Status.NEW, "source$index.json")
            },
        )
        val result = ImportFilesUseCaseImpl(songs, setlists, Logger.Standard).invoke(plan, ImportConflictResolution.KEEP_BOTH)
        assertTrue(result.isFailed)
        assertEquals(listOf("song.cho"), result.importedSongFileNames)
        assertEquals(listOf("set1.setlist.json"), result.importedSetlistFileNames)
        assertEquals(listOf("source2.json"), result.failedFileNames)
        assertEquals(listOf("source3.json"), result.unprocessedFileNames)
        assertEquals(listOf("set1.setlist.json"), setlists.adopted)
        assertEquals("song.cho", setlists.files.values.single().entries.single().songFileName)
    }

    @Test
    fun `cancellation still propagates and adopts the files already written`() = runTest {
        val songs = FakeSongRepository(mutableMapOf(), failingImport = 1, importFailure = CancellationException())
        val plan = ImportPlan(songs = listOf("one.cho", "two.cho").map {
            ImportPlan.SongEntry(it, A, ImportPlan.Status.NEW, null)
        })
        assertFailsWith<CancellationException> {
            ImportFilesUseCaseImpl(songs, FakeSetlistRepository(), Logger.Standard).invoke(plan, ImportConflictResolution.KEEP_BOTH)
        }
        assertEquals(listOf("one.cho"), songs.adopted)
    }

    /** Numbers a taken name the way the storage layer does, `x_2`, `x_3`…, unless told to replace it. */
    private fun MutableMap<String, *>.freeName(fileName: String, extension: String): String {
        val name = fileName.removeSuffix(extension)
        return generateSequence(1) { it + 1 }
            .map { if (it == 1) fileName else "${name}_$it$extension" }
            .first { it !in this }
    }

    private inner class FakeSongRepository(
        val files: MutableMap<String, String>,
        private val failingImport: Int? = null,
        private val importFailure: Exception = IllegalStateException("Full"),
    ) : SongRepositoryStub() {
        val importCalls = mutableListOf<Pair<String, Boolean>>()
        val adopted = mutableListOf<String>()
        var rescanCount = 0

        override suspend fun loadSongsIfNeeded() = files.keys.map { testSong(it) }

        override suspend fun rescan() {
            rescanCount++
        }

        override fun importFileName(fallbackTitle: String, text: String) = LibraryFiles.normalizedName(ChordProParser.parseMetadata(text).title ?: fallbackTitle) + ".cho"

        override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean): Song {
            if (importCalls.size == failingImport) throw importFailure
            importCalls += fileName to shouldReplace
            val storedName = if (shouldReplace) fileName else files.freeName(fileName, ".cho")
            files[storedName] = text
            return testSong(storedName)
        }

        override suspend fun adoptImported(songs: Collection<Song>) {
            adopted += songs.map { it.fileName }
        }
    }

    private open inner class FakeSetlistRepository(private val failingImport: Int? = null) : SetlistRepositoryStub() {
        val files = mutableMapOf<String, Setlist>()
        val adopted = mutableListOf<String>()

        override suspend fun loadSetlistsIfNeeded() = files.values.toList()

        override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean): Setlist {
            if (files.size == failingImport) throw IllegalStateException("Full")
            val storedName = if (shouldReplace) setlist.fileName else files.freeName(setlist.fileName, ".setlist.json")
            return setlist.copy(fileName = storedName).also { files[storedName] = it }
        }

        override suspend fun adoptImported(setlists: Collection<Setlist>) {
            adopted += setlists.map { it.fileName }
        }
    }

    private companion object {
        const val A = "{title: Foo}\nA\n"
        const val B = "{title: Foo}\nB\n"

        fun setlist(entries: List<String>) = testSetlist(fileName = "set.setlist.json", title = "Set", entries = entries)
    }
}
