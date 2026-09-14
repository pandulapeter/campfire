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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `refuses names that are paths`() = runBlocking {
        listOf("nested/a.cho", "..\\a.cho", ".", "..", "").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { fileStorage.readText(StorageDirectory.SONGS, name) }
        }
    }
}
