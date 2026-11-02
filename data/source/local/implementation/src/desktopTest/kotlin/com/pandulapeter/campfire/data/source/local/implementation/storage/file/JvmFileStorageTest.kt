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

import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What only the file system implementation the JVM platforms share does, against a temporary directory: Windows' names
 * and refusals, permissions, and its own temporary files. The contract every platform keeps is [FileStorageContractTest].
 */
class JvmFileStorageTest {

    private val root: File = Files.createTempDirectory("campfire-file-storage").toFile()
    private val fileStorage = JvmFileStorage(root)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `removes the temporary files an earlier run left behind`() = runBlocking {
        val songs = root.resolve("library/songs").apply { mkdirs() }
        listOf(".campfire-123.tmp", "a.cho.456.tmp").forEach { File(songs, it).apply { writeText("leftover"); setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000) } }
        File(songs, "notes.tmp").writeText("user")
        File(songs, "a.cho").writeText("song")

        assertEquals(listOf("a.cho"), JvmFileStorage(root).list(StorageDirectory.SONGS).map { it.name })
        assertFalse(File(songs, ".campfire-123.tmp").exists())
        assertFalse(File(songs, "a.cho.456.tmp").exists())
        assertTrue(File(songs, "notes.tmp").exists())
    }

    @Test
    fun `stores a Windows device name under an escaped one and reports the name it was given`() = runBlocking {
        val storage = JvmFileStorage(root, isWindows = true)
        storage.writeText(StorageDirectory.SONGS, "con.cho", "content")
        storage.writeText(StorageDirectory.SONGS, "_con.cho", "other")

        assertEquals(setOf("_con.cho", "__con.cho"), root.resolve("library/songs").list()?.toSet())
        assertEquals(listOf("_con.cho", "con.cho"), storage.list(StorageDirectory.SONGS).map { it.name })
        assertEquals("content", storage.readText(StorageDirectory.SONGS, "con.cho"))
        assertEquals("other", storage.readText(StorageDirectory.SONGS, "_con.cho"))
    }

    @Test
    fun `reports a file it cannot read as a failure rather than as missing`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "content")
        val file = root.walk().first { it.name == "a.cho" }
        file.setReadable(false)
        // Permissions mean nothing to a superuser, and the case cannot be set up there.
        if (file.canRead()) return@runBlocking

        assertFailsWith<LibraryStorageException> { fileStorage.readText(StorageDirectory.SONGS, "a.cho") }
        assertFailsWith<LibraryStorageException> { fileStorage.readBytes(StorageDirectory.SONGS, "a.cho") }
        file.setReadable(true)
    }

    @Test
    fun `reports a directory it cannot list as a storage failure`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "content")
        val directory = root.walk().first { it.name == "songs" && it.isDirectory }
        directory.setReadable(false)
        // Permissions mean nothing to a superuser or on Windows, and the case cannot be set up there.
        if (directory.list() != null) {
            directory.setReadable(true)
            return@runBlocking
        }

        assertFailsWith<LibraryStorageException> { fileStorage.list(StorageDirectory.SONGS) }
        assertFailsWith<LibraryStorageException> { fileStorage.listNames(StorageDirectory.SONGS) }
        directory.setReadable(true)
    }

    @Test
    fun `lists the files of a scan by name alone, sorted and without the temporary files of a write`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "b.cho", "{title: B}")
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "{title: A}")
        File(root.resolve("library/songs"), ".campfire-123.tmp").writeText("leftover")

        assertEquals(listOf(ScanEntry("a.cho", null), ScanEntry("b.cho", null)), fileStorage.listForScan(StorageDirectory.SONGS))
        assertEquals(emptyList(), fileStorage.listForScan(StorageDirectory.SETLISTS))
    }

    @Test
    fun `reports a directory a scan cannot list as a storage failure`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "content")
        val directory = root.walk().first { it.name == "songs" && it.isDirectory }
        directory.setReadable(false)
        // Permissions mean nothing to a superuser or on Windows, and the case cannot be set up there.
        if (directory.list() != null) {
            directory.setReadable(true)
            return@runBlocking
        }

        assertFailsWith<LibraryStorageException> { fileStorage.listForScan(StorageDirectory.SONGS) }
        directory.setReadable(true)
    }

    @Test
    fun `scans a Windows device name under the name it was given`() = runBlocking {
        val storage = JvmFileStorage(root, isWindows = true)
        storage.writeText(StorageDirectory.SONGS, "con.cho", "content")

        val entries = storage.listForScan(StorageDirectory.SONGS)

        assertEquals(listOf("con.cho"), entries.map { it.name })
        assertEquals("content", (storage.readScan(StorageDirectory.SONGS, entries, maxSize = 100L).single() as ScannedFile.Text).text)
    }

    @Test
    fun `reads a file removed just before the read as missing`() {
        assertNull(fileStorage.readingAsStorage("a.cho") { throw NoSuchFileException("a.cho") })
    }

    @Test
    fun `still reports any other failure of a read as a storage failure`() {
        assertFailsWith<LibraryStorageException> { fileStorage.readingAsStorage("a.cho") { throw AccessDeniedException("a.cho") } }
    }

    @Test
    fun `refuses to hold a name Windows cannot store`() {
        val storage = JvmFileStorage(root, isWindows = true)

        UNSTORABLE_ON_WINDOWS.forEach { assertFalse(storage.canHoldFileName(it), it) }
        assertTrue(storage.canHoldFileName("con.cho"))
        assertTrue(storage.canHoldFileName("катюша.cho"))
    }

    /** Every name that is a name at all, that is: one that is a path or nothing is refused everywhere, see the next test. */
    @Test
    fun `holds every name on a file system that is not Windows`() = runBlocking {
        val storage = JvmFileStorage(root, isWindows = false)

        UNSTORABLE_ON_WINDOWS.forEach { assertTrue(storage.canHoldFileName(it), it) }
        storage.writeText(StorageDirectory.SONGS, "who?.cho", "content")
        assertEquals("content", storage.readText(StorageDirectory.SONGS, "who?.cho"))
    }

    @Test
    fun `refuses to hold a name that is a path on every platform`() {
        listOf(true, false).forEach { isWindows ->
            val storage = JvmFileStorage(root, isWindows = isWindows)
            listOf("a\\b.cho", "a/b.cho", ".", "..", "").forEach { assertFalse(storage.canHoldFileName(it), "\"$it\" on Windows: $isWindows") }
        }
    }

    @Test
    fun `retries an operation Windows refuses while somebody else holds the file`() = runBlocking {
        var calls = 0

        val result = JvmFileStorage(root, isWindows = true).retryingWhileDenied {
            if (++calls < 3) throw AccessDeniedException("a.cho")
            "done"
        }

        assertEquals("done", result)
        assertEquals(3, calls)
    }

    @Test
    fun `gives up on an operation Windows keeps refusing`() = runBlocking {
        var calls = 0

        assertFailsWith<AccessDeniedException> {
            JvmFileStorage(root, isWindows = true).retryingWhileDenied { calls++; throw AccessDeniedException("a.cho") }
        }
        assertEquals(4, calls)
    }

    @Test
    fun `does not retry a refusal outside Windows`() = runBlocking {
        var calls = 0

        assertFailsWith<AccessDeniedException> {
            JvmFileStorage(root, isWindows = false).retryingWhileDenied { calls++; throw AccessDeniedException("a.cho") }
        }
        assertEquals(1, calls)
    }

    private companion object {
        val UNSTORABLE_ON_WINDOWS = listOf(
            "who?.cho",
            "a:b.cho",
            "a*b.cho",
            "a\".cho",
            "a<b.cho",
            "a>b.cho",
            "a|b.cho",
            "a.cho ",
            "a.cho.",
            "a\u0001.cho",
        )
    }
}
