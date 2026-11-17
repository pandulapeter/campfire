/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.model.isOutOfSpace
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.measureTimedValue

/**
 * Step 16 of the streaming archive import plan: archives far larger than the heap, and as many entries as the import
 * takes, imported in a JVM capped at 128 MiB (`largeArchiveImportTest`), each generated on the fly into a folder of its
 * own and imported through the app's own graph over a storage that counts what it holds ([MeteredFileStorage]). The
 * size of the largest is `campfire.largeArchiveTest` (600 MB in CI, 5 GB by hand before a release).
 */
class LargeArchiveImportTest {

    private val folder: File = Files.createTempDirectory("campfire-large-archive").toFile()

    @AfterTest
    fun tearDown() {
        folder.deleteRecursively()
    }

    @Test
    fun `a library archive far larger than the heap imports whole within the disk the staging reserved`() = runBlocking {
        val archive = timed("Writing the archive") { LargeArchives.library(File(folder, "library.zip"), size = largeArchiveSize) }
        assertEquals(archive.file.length() > 0xFFFFFFFFL, isZip64(archive.file), "Whether the archive is ZIP64 by its size")
        val storage = MeteredFileStorage(File(folder, "app"))
        LargeImportApp(storage).use { app ->
            app.watchLedger()

            val result = timed("Importing ${archive.file.length() shr 20} MiB") { app.import(archive.file) }

            assertEquals(false, result.isFailed)
            assertEquals(archive.songCount, result.importedSongFileNames.size)
            assertLibrary(archive, app.library())
            app.assertSettled()
            // The received archive beside what it unpacks (its nested archives' entries included), less the song texts,
            // which stay in memory: about twice the archive, more for one whose entries deflate well or that nests.
            val bound = archive.file.length() + archive.unpackedSize
            println("Peak staged: ${storage.peakStagedBytes shr 20} MiB for an archive of ${archive.file.length() shr 20} MiB")
            assertTrue(storage.peakStagedBytes <= bound, "The staging folder peaked at ${storage.peakStagedBytes} bytes, past $bound")
        }
    }

    @Test
    fun `a ZIP64 archive of 70000 small entries imports whole`() = runBlocking {
        val archive = ManyEntryArchives.songsWithPictures(File(folder, "songs.zip"), entryCount = 70_000)
        LargeImportApp(MeteredFileStorage(File(folder, "app"))).use { app ->
            app.watchLedger()

            val result = timed("Importing 70,000 entries") { app.import(archive.file) }

            assertEquals(archive.songCount, result.importedSongFileNames.size)
            assertLibrary(archive, app.library())
            app.assertSettled()
        }
    }

    @Test
    fun `an archive of as many entries as an import takes is read within the heap`() = runBlocking {
        val archive = ManyEntryArchives.musicFolder(File(folder, "music.zip"), entryCount = ImportLimits.MAX_ARCHIVE_ENTRIES)
        LargeImportApp(MeteredFileStorage(File(folder, "app"))).use { app ->
            val result = timed("Importing ${ImportLimits.MAX_ARCHIVE_ENTRIES} entries") { app.import(archive.file) }

            assertEquals(archive.songCount, result.importedSongFileNames.size)
            assertEquals(ImportLimits.MAX_ARCHIVE_ENTRIES - archive.songCount, result.skippedFileNames.size)
            app.assertSettled()
        }
    }

    @Test
    fun `an archive of one entry more than an import takes is refused whole`() = runBlocking {
        val archive = ManyEntryArchives.musicFolder(File(folder, "music.zip"), entryCount = ImportLimits.MAX_ARCHIVE_ENTRIES + 1)
        LargeImportApp(MeteredFileStorage(File(folder, "app"))).use { app ->
            val received = app.receive(archive.file)
            val plan = app.prepare(received, "music.zip")
            app.settle(received, plan)

            assertEquals(emptyList(), plan.songs)
            assertEquals(listOf("music.zip"), plan.skippedFileNames)
            assertEquals(emptyMap(), app.library())
            app.assertSettled()
        }
    }

    @Test
    fun `a central directory as large as an import holds is read within the heap and a larger one is refused whole`() = runBlocking {
        // A record is 46 bytes and its name, here as long as a song's name with its folders gets (about 250 bytes).
        val recordSize = 46 + LONG_NAME_LENGTH
        val within = ManyEntryArchives.musicFolder(File(folder, "within.zip"), entryCount = (ImportLimits.MAX_CENTRAL_DIRECTORY_SIZE / recordSize).toInt() - 100, nameLength = LONG_NAME_LENGTH)
        val past = ManyEntryArchives.musicFolder(File(folder, "past.zip"), entryCount = (ImportLimits.MAX_CENTRAL_DIRECTORY_SIZE / recordSize).toInt() + 100, nameLength = LONG_NAME_LENGTH)
        LargeImportApp(MeteredFileStorage(File(folder, "app"))).use { app ->
            val result = app.import(within.file)
            assertEquals(within.songCount, result.importedSongFileNames.size)

            val received = app.receive(past.file)
            val plan = app.prepare(received, "past.zip")
            app.settle(received, plan)
            assertEquals(listOf("past.zip"), plan.skippedFileNames)
            app.assertSettled()
        }
    }

    @Test
    fun `an archive declaring billions of entries is refused before anything is allocated for them`() = runBlocking {
        val file = File(folder, "declared.zip").also { ManyEntryArchives.declaringEntries(it, declaredCount = 1L shl 40) }
        LargeImportApp(MeteredFileStorage(File(folder, "app"))).use { app ->
            val received = app.receive(file)
            val plan = app.prepare(received, "declared.zip")
            app.settle(received, plan)

            assertEquals(listOf("declared.zip"), plan.skippedFileNames)
            app.assertSettled()
        }
    }

    @Test
    fun `a library past what a zip holds without ZIP64 comes back file for file through its export`() = runBlocking {
        // More files than a zip counts in 16 bits; by hand, at `campfire.largeArchiveTest`'s size, more bytes than it
        // counts in 32, carried by a few hundred songs' recordings.
        val isBySize = largeArchiveSize > ROUND_TRIP_LIBRARY_SIZE
        val source = MeteredFileStorage(File(folder, "source").also { root ->
            if (isBySize) {
                ManyEntryArchives.fillLibrary(root, songCount = 300, picturesPerSong = 2, attachmentSize = largeArchiveSize)
            } else {
                val count = ManyEntryArchives.fillLibrary(root, songCount = 3_000, picturesPerSong = 22, attachmentSize = 0)
                assertTrue(count > 65_535, "Only $count files in the library")
            }
        })
        val export = LargeImportApp(source).use { app -> timed("Exporting") { app.exportLibrary() } }
        assertTrue(isZip64(export), "The export is not ZIP64")
        LargeImportApp(MeteredFileStorage(File(folder, "target"))).use { app ->
            app.watchLedger()

            val result = timed("Importing the export of ${export.length() shr 20} MiB") { app.import(export) }
            println("Peak staged: ${app.storage.peakStagedBytes shr 20} MiB for an export of ${export.length() shr 20} MiB")

            assertEquals(false, result.isFailed)
            assertSameLibrary(LargeImportApp.libraryOf(source.root), app.library())
            app.assertSettled()
        }
    }

    @Test
    fun `a preparation cancelled anywhere leaves no staged file and the library untouched`() = runBlocking {
        val archive = mediumArchive()
        val storage = MeteredFileStorage(File(folder, "app"))
        LargeImportApp(storage).use { app ->
            app.import(LargeArchives.library(File(folder, "first.zip"), size = 0, songCount = 20, seed = 2, hasLimitSizedFiles = false).file)
            val library = app.library()
            var changes = 0
            storage.beforeChange = { _, _ -> changes++ }
            app.preparation(archive.file).let { (staged, plan) -> app.settle(staged, plan) }
            val total = changes
            val random = Random(16)
            (List(CANCELLATIONS) { 1 + random.nextInt(total) } + listOf(1, total - 1)).sorted().forEach { cancelAt ->
                changes = 0
                var staged: StagedFile? = null
                val preparing = async {
                    staged = app.receive(archive.file)
                    app.prepare(checkNotNull(staged))
                }
                storage.beforeChange = { _, _ -> if (++changes == cancelAt) preparing.cancel() }

                val plan = try {
                    preparing.await()
                } catch (_: CancellationException) {
                    null
                }
                storage.beforeChange = { _, _ -> }
                staged?.let { app.settle(it, plan) }

                assertEquals(library, app.library(), "The library after a cancellation at change $cancelAt of $total")
                app.assertSettled("after a cancellation at change $cancelAt of $total")
            }
        }
    }

    @Test
    fun `a disk that fills during the preparation leaves no staged file and the library untouched`() = runBlocking {
        val archive = mediumArchive()
        val peak = MeteredFileStorage(File(folder, "measured")).let { storage ->
            LargeImportApp(storage).use { app -> app.preparation(archive.file).let { (staged, plan) -> app.settle(staged, plan) } }
            storage.peakStagedBytes
        }
        val random = Random(17)
        repeat(FILLED_DISKS) { attempt ->
            val storage = MeteredFileStorage(File(folder, "app$attempt"))
            LargeImportApp(storage).use { app ->
                app.import(LargeArchives.library(File(folder, "first$attempt.zip"), size = 0, songCount = 20, seed = 2, hasLimitSizedFiles = false).file)
                val library = app.library()
                // A disk that says it has room for everything (as Safari's estimate may), and fills somewhere between
                // receiving the archive and the last file it unpacks.
                storage.capacity = storage.usedBytes + peak / 20 + random.nextLong(peak * 9 / 10)
                storage.reportedSpace = { Long.MAX_VALUE / 4 }

                val failure = try {
                    app.preparation(archive.file).also { (staged, plan) -> app.settle(staged, plan) }
                    null
                } catch (exception: Exception) {
                    exception
                }

                assertTrue(failure?.isOutOfSpace() == true, "A disk of ${storage.capacity} bytes failed the preparation with $failure")
                assertEquals(library, app.library())
                app.assertSettled("on a disk of ${storage.capacity} bytes")
            }
        }
    }

    @Test
    fun `an archive the in-memory path imports needs twice its size and the margin and names what it needs with less`() = runBlocking {
        val archive = File(folder, "eighty.zip")
        val pictures = (0 until 8).map { LargeArchives.picture(seed = it, size = 7_500_000, isCompressible = false) }
        val recording = LargeArchives.noise(seed = 9, size = 18_000_000, isCompressible = false)
        val song = (pictures.indices.joinToString("") { "{image: src=p$it.png}\n" } + "{meta: attachment 0123456789abcdef.mp3 Take}\n").let { "{title: Eighty}\n$it" }
        LargeArchives.write(
            archive,
            listOf(suspend { ZipEntry("eighty.cho", song.encodeToByteArray()) }) +
                pictures.mapIndexed { index, bytes -> suspend { ZipEntry("p$index.png", bytes) } } +
                listOf(suspend { ZipEntry("0123456789abcdef.mp3", recording) }),
        )
        assertTrue(archive.length() in 78_000_000..ImportLimits.MAX_ARCHIVE_SIZE, "An archive of ${archive.length()} bytes")
        val twice = 2 * archive.length()
        val enough = twice + ImportLimits.minFreeSpaceFor(twice, isQuota = false)

        val named = LargeImportApp(MeteredFileStorage(File(folder, "short"), capacity = enough * 3 / 4)).use { app ->
            val failure = assertNotNull(runCatching { app.preparation(archive) }.exceptionOrNull(), "An import on too small a disk")
            app.assertSettled("after the refusal")
            assertTrue(failure.isOutOfSpace(), "Refused with $failure")
            generateSequence<Throwable>(failure) { it.cause }.filterIsInstance<StagingRefusedException.NoSpace>().first().neededBytes
        }
        assertTrue(named in (enough * 3 / 4 + 1)..enough, "Named $named bytes, where $enough are enough")

        listOf(enough, named).forEach { capacity ->
            LargeImportApp(MeteredFileStorage(File(folder, "disk$capacity"), capacity = capacity)).use { app ->
                val result = app.import(archive)
                assertEquals(listOf("eighty.cho"), result.importedSongFileNames, "Imported on a disk of $capacity bytes")
                assertEquals(pictures.size + 1, app.library().keys.count { it.startsWith("files/") }, "Files imported on a disk of $capacity bytes")
                app.assertSettled()
            }
        }
    }

    @Test
    fun `an import killed during its write and repeated after a fresh start leaves the library a clean import leaves`() = runBlocking {
        val archive = mediumArchive()
        val clean = LargeImportApp(MeteredFileStorage(File(folder, "clean"))).use { app ->
            app.import(archive.file)
            app.library()
        }
        val random = Random(18)
        repeat(KILLS) { attempt ->
            val storage = MeteredFileStorage(File(folder, "killed$attempt"))
            val killAfter = 1 + random.nextInt(archive.songCount - 1)
            LargeImportApp(storage).use { app ->
                val plan = app.preparation(archive.file).second
                var songs = 0
                var isKilled = false
                storage.beforeChange = { directory, _ ->
                    if (directory == StorageDirectory.SONGS && ++songs > killAfter) isKilled = true
                    if (isKilled) throw ProcessKilled()
                }
                runCatching { app.apply(plan) }
                assertTrue(isKilled, "The write was not stopped")
            }
            storage.beforeChange = { _, _ -> }
            LargeImportApp(storage).use { app ->
                app.clearStaging()
                app.import(archive.file)

                assertSameLibrary(clean, app.library(), "after a write killed after $killAfter songs and done again")
                app.assertSettled()
            }
        }
    }

    @Test
    fun `a staged file gone before Import fails it with nothing written`() = runBlocking {
        val archive = mediumArchive()
        val storage = MeteredFileStorage(File(folder, "app"))
        LargeImportApp(storage).use { app ->
            val (staged, plan) = app.preparation(archive.file)
            val gone = plan.stagedFiles.distinct()[plan.stagedFiles.distinct().size / 2]
            File(storage.pathOf(StorageDirectory.IMPORTS, gone.name)).delete()

            val failure = runCatching { app.apply(plan) }.exceptionOrNull()
            app.settle(staged, plan)

            assertEquals("StagedFileMissingException", failure?.let { it::class.simpleName }, "Import ended with $failure")
            assertEquals(emptyMap(), app.library())
            app.assertSettled()
        }
    }

    /** About 60 MB with nested archives and a few hundred songs: enough to cancel, fill, and kill an import anywhere. */
    private suspend fun mediumArchive() = LargeArchives.library(File(folder, "medium.zip"), size = 40L shl 20, songCount = 300, hasLimitSizedFiles = false)

    /**
     * Whether [file] ends in ZIP64's records: the locator just before the end record, which `ZipWriter` writes no comment
     * after.
     */
    private fun isZip64(file: File) = RandomAccessFile(file, "r").use { input ->
        input.seek(file.length() - END_RECORD_SIZE - ZIP64_LOCATOR_SIZE)
        Integer.reverseBytes(input.readInt()) == ZIP64_LOCATOR_SIGNATURE
    }

    /** Received and prepared, not yet imported or settled. */
    private suspend fun LargeImportApp.preparation(file: File): Pair<StagedFile, ImportPlan> {
        val staged = receive(file)
        val plan = try {
            prepare(staged, file.name)
        } catch (exception: Throwable) {
            settle(staged)
            throw exception
        }
        return staged to plan
    }

    /** Fails the test where the staging folder ever holds more than the requests with files in it have reserved. */
    private fun LargeImportApp.watchLedger() {
        storage.onStagedGrowth = { staged ->
            val reserved = stagingReserved()
            if (staged > reserved) fail("The staging folder held $staged bytes, past the $reserved the ledger reserved")
        }
    }

    private suspend fun LargeImportApp.assertSettled(context: String = "") {
        assertEquals(emptyList(), storage.stagedNames(), "The staging folder $context")
        assertEquals(0L, ledgerOutstanding(), "What the ledger holds $context")
    }

    /** [actual] holds every file of [expected] with the same bytes and nothing else, told by the first few differences. */
    private fun assertSameLibrary(expected: Map<String, String>, actual: Map<String, String>, context: String = "") {
        val missing = expected.keys - actual.keys
        val extra = actual.keys - expected.keys
        val changed = expected.keys.filter { it in actual && actual[it] != expected[it] }
        if (missing.isNotEmpty() || extra.isNotEmpty() || changed.isNotEmpty()) {
            fun List<String>.sample() = "$size: ${sorted().take(SAMPLE_SIZE)}"
            fail("The library $context differs: missing ${missing.toList().sample()}, extra ${extra.toList().sample()}, changed ${changed.sample()}")
        }
    }

    private fun assertLibrary(archive: LargeArchive, library: Map<String, String>) {
        val songs = library.keys.filter { it.startsWith("songs/") }
        val setlists = library.keys.filter { it.startsWith("setlists/") }
        val files = library.filterKeys { it.startsWith("files/") }
        assertEquals(archive.songCount, songs.size, "Songs in the library")
        assertEquals(archive.setlistCount, setlists.size, "Setlists in the library")
        assertEquals(archive.fileHashes, files.values.toSet(), "The pictures and attachments in the library, by their hashes")
        assertEquals(files.size, files.values.toSet().size, "One library file per content")
        files.forEach { (path, hash) -> assertTrue(hash.startsWith(path.substringAfter("files/").substringBefore('.')), "$path is not named by its content") }
    }

    private suspend fun <T> timed(what: String, block: suspend () -> T): T {
        val (value, duration) = measureTimedValue { block() }
        println("$what: $duration")
        return value
    }

    /** What the storage throws for every change once the process it stands for is gone. */
    private class ProcessKilled : RuntimeException("The process was killed.")

    private companion object {

        /** `campfire.largeArchiveTest`: a size in bytes, or with a `k`, `m`, or `g` after it. */
        val largeArchiveSize: Long = System.getProperty("campfire.largeArchiveTest", "600m").trim().lowercase().let { value ->
            val shift = when (value.lastOrNull()) {
                'k' -> 10
                'm' -> 20
                'g' -> 30
                else -> 0
            }
            (if (shift == 0) value else value.dropLast(1)).toLong() shl shift
        }

        /** Past this, the export round trip is of a library of `campfire.largeArchiveTest`'s size (by hand). */
        const val ROUND_TRIP_LIBRARY_SIZE = 1L shl 30
        const val CANCELLATIONS = 10
        const val FILLED_DISKS = 6
        const val KILLS = 3
        const val SAMPLE_SIZE = 5
        const val LONG_NAME_LENGTH = 250
        const val END_RECORD_SIZE = 22
        const val ZIP64_LOCATOR_SIZE = 20
        const val ZIP64_LOCATOR_SIGNATURE = 0x07064B50
    }
}
