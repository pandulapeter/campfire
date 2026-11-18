/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import com.pandulapeter.campfire.data.model.library.normalizedToNfc
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.arrivingCollisionSuffix
import com.pandulapeter.campfire.data.source.local.implementation.moveFile
import com.pandulapeter.campfire.data.source.local.implementation.uniqueName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The [FileStorage] contract every platform's implementation keeps, through the interface alone, run by a subclass per
 * platform over a fresh, empty root: a read answers null only for a file that is not there, since sync plans a missing
 * file as a deletion that reaches every device.
 */
abstract class FileStorageContractTest {

    private val fileStorage by lazy { newStorage() }

    /** A storage over a fresh, empty root of its own, removed by [tearDown]. Called once per test. */
    abstract fun newStorage(): FileStorage

    /** Deletes [directory] with everything in it, the way the Files app or the user would, without the storage knowing. */
    abstract suspend fun deleteDirectoryBehindItsBack(directory: StorageDirectory)

    /** Creates a directory called [name] inside [directory], which the storage itself never makes. */
    abstract suspend fun makeDirectoryUnderFileName(directory: StorageDirectory, name: String)

    @AfterTest
    open fun tearDown() = Unit

    @Test
    fun `writes and lists and reads back text files`() = runTest {
        fileStorage.writeText(StorageDirectory.SONGS, "b.cho", "{title: B}")
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "{title: A}")

        val files = fileStorage.list(StorageDirectory.SONGS)

        assertEquals(listOf("a.cho", "b.cho"), files.map { it.name })
        assertEquals("{title: A}".length.toLong(), files.first().size)
        assertTrue(files.first().lastModified > 0L)
        assertEquals("{title: A}", fileStorage.readText(StorageDirectory.SONGS, "a.cho"))
        assertTrue(fileStorage.exists(StorageDirectory.SONGS, "a.cho"))
    }

    @Test
    fun `reports one file the way the listing does`() = runTest {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "{title: A}")

        assertEquals(fileStorage.list(StorageDirectory.SONGS).single(), fileStorage.info(StorageDirectory.SONGS, "a.cho"))
        assertNull(fileStorage.info(StorageDirectory.SONGS, "missing.cho"))
    }

    @Test
    fun `keeps the directories apart`() = runTest {
        fileStorage.writeText(StorageDirectory.SONGS, "shared.txt", "song")
        fileStorage.writeText(StorageDirectory.SETLISTS, "shared.txt", "setlist")
        fileStorage.writeText(StorageDirectory.PREFERENCES, "shared.txt", "preference")

        assertEquals("song", fileStorage.readText(StorageDirectory.SONGS, "shared.txt"))
        assertEquals("setlist", fileStorage.readText(StorageDirectory.SETLISTS, "shared.txt"))
        assertEquals("preference", fileStorage.readText(StorageDirectory.PREFERENCES, "shared.txt"))
    }

    @Test
    fun `overwrites an existing file`() = runTest {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "first")
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "second")

        assertEquals("second", fileStorage.readText(StorageDirectory.SONGS, "a.cho"))
        assertEquals(1, fileStorage.list(StorageDirectory.SONGS).size)
    }

    @Test
    fun `lets concurrent writes of one file all finish and keeps one of them whole`() = runTest {
        val texts = List(16) { index -> "{title: Writer $index}\n" + "[Am]line $index\n".repeat(2000) }

        texts.map { text -> async(Dispatchers.Default) { fileStorage.writeText(StorageDirectory.SONGS, "a.cho", text) } }.awaitAll()

        assertTrue(fileStorage.readText(StorageDirectory.SONGS, "a.cho") in texts)
        assertEquals(listOf("a.cho"), fileStorage.list(StorageDirectory.SONGS).map { it.name })
        assertEquals(listOf("a.cho"), fileStorage.listNames(StorageDirectory.SONGS))
    }

    @Test
    fun `round trips bytes`() = runTest {
        val bytes = ByteArray(512) { (it % 256 - 128).toByte() }
        fileStorage.writeBytes(StorageDirectory.SONGS, "library.zip", bytes)

        assertContentEquals(bytes, fileStorage.readBytes(StorageDirectory.SONGS, "library.zip"))
    }

    @Test
    fun `saves a file whose name is nearly as long as the file system allows`() = runTest {
        val name = "a".repeat(240) + ".cho"
        fileStorage.writeText(StorageDirectory.SONGS, name, "content")

        assertEquals("content", fileStorage.readText(StorageDirectory.SONGS, name))
    }

    @Test
    fun `strips the byte order mark`() = runTest {
        fileStorage.writeBytes(StorageDirectory.SONGS, "a.cho", "﻿{title: A}".encodeToByteArray())

        assertEquals("{title: A}", fileStorage.readText(StorageDirectory.SONGS, "a.cho"))
    }

    @Test
    fun `reads a file that is not UTF-8 as Windows-1252`() = runTest {
        // "{title: Café}" in ISO-8859-1, where the é is the one byte 0xE9.
        val bytes = "{title: Caf".encodeToByteArray() + byteArrayOf(0xE9.toByte()) + "}".encodeToByteArray()
        fileStorage.writeBytes(StorageDirectory.SONGS, "a.cho", bytes)

        assertEquals("{title: Café}", fileStorage.readText(StorageDirectory.SONGS, "a.cho"))
    }

    @Test
    fun `deletes a file and ignores a missing one`() = runTest {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "content")

        fileStorage.delete(StorageDirectory.SONGS, "a.cho")
        fileStorage.delete(StorageDirectory.SONGS, "a.cho")

        assertFalse(fileStorage.exists(StorageDirectory.SONGS, "a.cho"))
        assertEquals(emptyList(), fileStorage.list(StorageDirectory.SONGS))
    }

    @Test
    fun `returns null for a missing file`() = runTest {
        assertNull(fileStorage.readText(StorageDirectory.SONGS, "missing.cho"))
        assertNull(fileStorage.readBytes(StorageDirectory.SONGS, "missing.cho"))
        assertFalse(fileStorage.exists(StorageDirectory.SONGS, "missing.cho"))
        assertEquals(emptyList(), fileStorage.list(StorageDirectory.SONGS))
    }

    @Test
    fun `answers a directory deleted under it as empty and recreates it on the next write`() = runTest {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "content")
        deleteDirectoryBehindItsBack(StorageDirectory.SONGS)

        assertEquals(emptyList(), fileStorage.list(StorageDirectory.SONGS))
        assertNull(fileStorage.readText(StorageDirectory.SONGS, "a.cho"))
        fileStorage.writeText(StorageDirectory.SONGS, "b.cho", "content")

        assertEquals(listOf("b.cho"), fileStorage.list(StorageDirectory.SONGS).map { it.name })
    }

    @Test
    fun `reads a directory under a file's name as missing`() = runTest {
        makeDirectoryUnderFileName(StorageDirectory.SONGS, "a.cho")

        assertNull(fileStorage.readText(StorageDirectory.SONGS, "a.cho"))
        assertNull(fileStorage.readBytes(StorageDirectory.SONGS, "a.cho"))
        assertEquals(emptyList(), fileStorage.list(StorageDirectory.SONGS))
    }

    @Test
    fun `lists the files of a scan sorted by name`() = runTest {
        fileStorage.writeText(StorageDirectory.SONGS, "b.cho", "{title: B}")
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "{title: A}")

        assertEquals(listOf("a.cho", "b.cho"), fileStorage.listForScan(StorageDirectory.SONGS).map { it.name })
        assertEquals(emptyList(), fileStorage.listForScan(StorageDirectory.SETLISTS))
    }

    @Test
    fun `reads each file of a scan with what the listing reports about it`() = runTest {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "{title: A}")
        fileStorage.writeText(StorageDirectory.SONGS, "large.cho", "{title: Large}")
        fileStorage.writeText(StorageDirectory.SONGS, "gone.cho", "{title: Gone}")
        val entries = fileStorage.listForScan(StorageDirectory.SONGS)
        val listed = fileStorage.list(StorageDirectory.SONGS).associateBy { it.name }
        fileStorage.delete(StorageDirectory.SONGS, "gone.cho")
        makeDirectoryUnderFileName(StorageDirectory.SONGS, "folder.cho")

        val answers = fileStorage.readScan(StorageDirectory.SONGS, entries + ScanEntry("folder.cho", null), maxSize = 12L)

        assertEquals(
            listOf(
                ScannedFile.Text(listed.getValue("a.cho"), "{title: A}"),
                ScannedFile.Missing,
                ScannedFile.TooLarge(listed.getValue("large.cho")),
                ScannedFile.Missing,
            ),
            answers,
        )
    }

    @Test
    fun `skips a name that is taken elsewhere`() = runTest {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "content")

        assertEquals("a (3).cho", fileStorage.uniqueName(StorageDirectory.SONGS, "a.cho", ::arrivingCollisionSuffix) { it == "a (2).cho" })
    }

    @Test
    fun `lists the directory for a rename that only changes case`() = runTest {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "content")

        assertEquals("A.cho", fileStorage.uniqueName(StorageDirectory.SONGS, "A.cho", currentName = "a.cho"))
    }

    @Test
    fun `keeps a file moved to its own name in another case and Unicode form`() = runTest {
        // `Ένα.cho` decomposed, as macOS hands it out, moved to the composed lowercase name the library would give it.
        val current = "\u0395\u0301να.cho"
        val new = "ένα.cho"
        fileStorage.writeText(StorageDirectory.SONGS, current, "{title: Ένα}\n")

        fileStorage.moveFile(StorageDirectory.SONGS, currentName = current, newName = new) {
            fileStorage.writeText(StorageDirectory.SONGS, it, "{title: Ένα}\n")
        }

        assertEquals("{title: Ένα}\n", fileStorage.readText(StorageDirectory.SONGS, new))
        assertEquals(listOf(new), fileStorage.list(StorageDirectory.SONGS).map { it.name.normalizedToNfc() })
    }

    @Test
    fun `refuses names that are paths`() = runTest {
        listOf("nested/a.cho", "..\\a.cho", ".", "..", "").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { fileStorage.readText(StorageDirectory.SONGS, name) }
        }
    }

    @Test
    fun `a sink writes a file front to back and replaces one of the same name`() = runTest {
        fileStorage.writeBytes(StorageDirectory.EXPORTS, "songs.zip", ByteArray(1_000) { 1 })
        val first = ByteArray(70_000) { (it % 251).toByte() }
        val second = "the end".encodeToByteArray()

        val sink = fileStorage.openSink(StorageDirectory.EXPORTS, "songs.zip")
        sink.write(first, 0, first.size)
        sink.write(second, 4, 3)
        sink.close()

        assertEquals(first.size + 3L, sink.position)
        assertContentEquals(first + "end".encodeToByteArray(), fileStorage.readBytes(StorageDirectory.EXPORTS, "songs.zip"))
        // Windows separates the folders with a backslash.
        assertTrue(fileStorage.pathOf(StorageDirectory.EXPORTS, "songs.zip").replace('\\', '/').endsWith("exports/songs.zip"))
    }

    @Test
    fun `an abandoned sink leaves no file behind`() = runTest {
        val sink = fileStorage.openSink(StorageDirectory.EXPORTS, "songs.pdf")
        sink.write(ByteArray(10), 0, 10)

        sink.abandon()

        assertFalse(fileStorage.exists(StorageDirectory.EXPORTS, "songs.pdf"))
    }

    @Test
    fun `a sink writes into the imports folder`() = runTest {
        val sink = fileStorage.openSink(StorageDirectory.IMPORTS, "archive.zip")
        sink.write(BYTES, 0, BYTES.size)
        sink.close()

        assertContentEquals(BYTES, fileStorage.readBytes(StorageDirectory.IMPORTS, "archive.zip"))
    }

    @Test
    fun `a source reads at every offset and stops at the end of the file`() = runTest {
        fileStorage.writeBytes(StorageDirectory.FILES, "a.bin", BYTES)
        val source = assertNotNull(fileStorage.openSource(StorageDirectory.FILES, "a.bin"))

        try {
            assertEquals(BYTES.size.toLong(), source.size)
            listOf(0, 1, 4_095, 65_536, BYTES.size - 7).forEach { position ->
                val buffer = ByteArray(16) { -1 }
                val read = source.read(position.toLong(), buffer, 3, 10)
                val expected = minOf(10, BYTES.size - position)
                assertEquals(expected, read, "at $position")
                assertContentEquals(BYTES.copyOfRange(position, position + expected), buffer.copyOfRange(3, 3 + expected), "at $position")
                assertEquals(-1, buffer[2], "at $position")
            }
            assertEquals(0, source.read(BYTES.size.toLong(), ByteArray(4), 0, 4))
            assertEquals(0, source.read(BYTES.size + 100L, ByteArray(4), 0, 4))
        } finally {
            source.close()
        }
    }

    @Test
    fun `a source of a missing file or of a directory is none`() = runTest {
        makeDirectoryUnderFileName(StorageDirectory.FILES, "folder.bin")

        assertNull(fileStorage.openSource(StorageDirectory.FILES, "missing.bin"))
        assertNull(fileStorage.openSource(StorageDirectory.FILES, "folder.bin"))
    }

    @Test
    fun `a source of a file deleted meanwhile reads what it was opened on or fails as storage`() = runTest {
        fileStorage.writeBytes(StorageDirectory.FILES, "a.bin", BYTES)
        val source = assertNotNull(fileStorage.openSource(StorageDirectory.FILES, "a.bin"))
        fileStorage.delete(StorageDirectory.FILES, "a.bin")

        try {
            val buffer = ByteArray(100)
            val read = source.read(1_000, buffer, 0, buffer.size)
            assertEquals(100, read)
            assertContentEquals(BYTES.copyOfRange(1_000, 1_100), buffer)
        } catch (_: LibraryStorageException) {
        } finally {
            source.close()
        }
    }

    @Test
    fun `a move puts the file under its new name over nothing and over a file there`() = runTest {
        fileStorage.writeBytes(StorageDirectory.IMPORTS, "first.bin", BYTES)
        fileStorage.writeBytes(StorageDirectory.IMPORTS, "second.bin", "second".encodeToByteArray())
        fileStorage.writeBytes(StorageDirectory.FILES, "taken.bin", "old".encodeToByteArray())

        fileStorage.moveInto(StorageDirectory.IMPORTS, "first.bin", StorageDirectory.FILES, "new.bin")
        fileStorage.moveInto(StorageDirectory.IMPORTS, "second.bin", StorageDirectory.FILES, "taken.bin")

        assertContentEquals(BYTES, fileStorage.readBytes(StorageDirectory.FILES, "new.bin"))
        assertEquals("second", fileStorage.readText(StorageDirectory.FILES, "taken.bin"))
        assertEquals(emptyList(), fileStorage.list(StorageDirectory.IMPORTS))
        assertEquals(listOf("new.bin", "taken.bin"), fileStorage.list(StorageDirectory.FILES).map { it.name })
    }

    @Test
    fun `a move or a copy of a missing file fails as storage`() = runTest {
        assertFailsWith<LibraryStorageException> { fileStorage.moveInto(StorageDirectory.IMPORTS, "missing.bin", StorageDirectory.FILES, "a.bin") }
        assertFailsWith<LibraryStorageException> { fileStorage.copyInto(StorageDirectory.DRAFT_IMAGES, "missing.bin", StorageDirectory.FILES, "a.bin") }
        assertFalse(fileStorage.exists(StorageDirectory.FILES, "a.bin"))
    }

    @Test
    fun `a copy keeps the original and replaces a file there`() = runTest {
        fileStorage.writeBytes(StorageDirectory.DRAFT_IMAGES, "a.bin", BYTES)
        fileStorage.writeBytes(StorageDirectory.FILES, "a.bin", "old".encodeToByteArray())

        fileStorage.copyInto(StorageDirectory.DRAFT_IMAGES, "a.bin", StorageDirectory.FILES, "a.bin")

        assertContentEquals(BYTES, fileStorage.readBytes(StorageDirectory.DRAFT_IMAGES, "a.bin"))
        assertContentEquals(BYTES, fileStorage.readBytes(StorageDirectory.FILES, "a.bin"))
        assertEquals(listOf("a.bin"), fileStorage.list(StorageDirectory.FILES).map { it.name })
    }

    @Test
    fun `there is space available in a folder not created yet where the storage can tell`() = runTest {
        assertTrue((fileStorage.availableSpace(StorageDirectory.IMPORTS) ?: Long.MAX_VALUE) > 0)
    }

    private companion object {
        val BYTES = ByteArray(200_000) { (it * 31 % 251).toByte() }
    }
}
