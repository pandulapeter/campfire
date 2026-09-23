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
import com.pandulapeter.campfire.data.source.local.implementation.arrivingCollisionSuffix
import com.pandulapeter.campfire.data.source.local.implementation.uniqueName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Exercises the file system implementation the JVM platforms share against a temporary directory. The iOS and web
 * implementations of the same interface can only be verified by running the app.
 */
class JvmFileStorageTest {

    private val root: File = Files.createTempDirectory("campfire-file-storage").toFile()
    private val fileStorage = JvmFileStorage(root)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `writes, lists and reads back text files`() = runBlocking {
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
    fun `reports one file the way the listing does`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "{title: A}")

        assertEquals(fileStorage.list(StorageDirectory.SONGS).single(), fileStorage.info(StorageDirectory.SONGS, "a.cho"))
        assertNull(fileStorage.info(StorageDirectory.SONGS, "missing.cho"))
    }

    @Test
    fun `keeps the directories apart`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "shared.txt", "song")
        fileStorage.writeText(StorageDirectory.SETLISTS, "shared.txt", "setlist")
        fileStorage.writeText(StorageDirectory.PREFERENCES, "shared.txt", "preference")

        assertEquals("song", fileStorage.readText(StorageDirectory.SONGS, "shared.txt"))
        assertEquals("setlist", fileStorage.readText(StorageDirectory.SETLISTS, "shared.txt"))
        assertEquals("preference", fileStorage.readText(StorageDirectory.PREFERENCES, "shared.txt"))
    }

    @Test
    fun `overwrites an existing file`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "first")
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "second")

        assertEquals("second", fileStorage.readText(StorageDirectory.SONGS, "a.cho"))
        assertEquals(1, fileStorage.list(StorageDirectory.SONGS).size)
    }

    @Test
    fun `lets concurrent writes of one file all finish, keeping one of them whole`() = runBlocking {
        val texts = List(16) { index -> "{title: Writer $index}\n" + "[Am]line $index\n".repeat(2000) }

        texts.map { text -> async(Dispatchers.Default) { fileStorage.writeText(StorageDirectory.SONGS, "a.cho", text) } }.awaitAll()

        assertTrue(fileStorage.readText(StorageDirectory.SONGS, "a.cho") in texts)
        assertEquals(listOf("a.cho"), fileStorage.list(StorageDirectory.SONGS).map { it.name })
        assertEquals(listOf("a.cho"), root.resolve("library/songs").list()?.toList())
    }

    @Test
    fun `round trips bytes`() = runBlocking {
        val bytes = ByteArray(512) { (it % 256 - 128).toByte() }
        fileStorage.writeBytes(StorageDirectory.SONGS, "library.zip", bytes)

        assertContentEquals(bytes, fileStorage.readBytes(StorageDirectory.SONGS, "library.zip"))
    }

    @Test
    fun `saves a file whose name is nearly as long as the file system allows`() = runBlocking {
        val name = "a".repeat(240) + ".cho"
        fileStorage.writeText(StorageDirectory.SONGS, name, "content")

        assertEquals("content", fileStorage.readText(StorageDirectory.SONGS, name))
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
    fun `strips the byte order mark`() = runBlocking {
        fileStorage.writeBytes(StorageDirectory.SONGS, "a.cho", "\uFEFF{title: A}".encodeToByteArray())

        assertEquals("{title: A}", fileStorage.readText(StorageDirectory.SONGS, "a.cho"))
    }

    @Test
    fun `reads a file that is not UTF-8 as Windows-1252`() = runBlocking {
        fileStorage.writeBytes(StorageDirectory.SONGS, "a.cho", "{title: Café}".toByteArray(Charsets.ISO_8859_1))

        assertEquals("{title: Café}", fileStorage.readText(StorageDirectory.SONGS, "a.cho"))
    }

    @Test
    fun `deletes a file and ignores a missing one`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "content")

        fileStorage.delete(StorageDirectory.SONGS, "a.cho")
        fileStorage.delete(StorageDirectory.SONGS, "a.cho")

        assertFalse(fileStorage.exists(StorageDirectory.SONGS, "a.cho"))
        assertEquals(emptyList(), fileStorage.list(StorageDirectory.SONGS))
    }

    @Test
    fun `returns null for a missing file`() = runBlocking {
        assertNull(fileStorage.readText(StorageDirectory.SONGS, "missing.cho"))
        assertNull(fileStorage.readBytes(StorageDirectory.SONGS, "missing.cho"))
        assertFalse(fileStorage.exists(StorageDirectory.SONGS, "missing.cho"))
        assertEquals(emptyList(), fileStorage.list(StorageDirectory.SONGS))
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
    fun `skips a name that is taken elsewhere`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "content")

        assertEquals("a (3).cho", fileStorage.uniqueName(StorageDirectory.SONGS, "a.cho", ::arrivingCollisionSuffix) { it == "a (2).cho" })
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

    @Test
    fun `numbers a collision without listing the directory`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "first")
        fileStorage.writeText(StorageDirectory.SONGS, "a_2.cho", "second")
        val unlisted = object : FileStorage by fileStorage {
            override suspend fun listNames(directory: StorageDirectory): List<String> = fail("The directory was listed.")
        }

        assertEquals("a_3.cho", unlisted.uniqueName(StorageDirectory.SONGS, "a.cho"))
    }

    @Test
    fun `lists the directory for a rename that only changes case`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "content")

        assertEquals("A.cho", fileStorage.uniqueName(StorageDirectory.SONGS, "A.cho", currentName = "a.cho"))
    }

    @Test
    fun `refuses names that are paths`() = runBlocking {
        listOf("nested/a.cho", "..\\a.cho", ".", "..", "").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { fileStorage.readText(StorageDirectory.SONGS, name) }
        }
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
