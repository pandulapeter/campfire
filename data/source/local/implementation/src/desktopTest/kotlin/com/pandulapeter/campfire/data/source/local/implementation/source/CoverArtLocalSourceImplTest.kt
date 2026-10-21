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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.InMemoryFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StoredFileInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The copies of covers are a cache: a failure costs a cover rather than an error, and none of them belongs in a device
 * backup. The prune runs while thumbnails are being saved, so it must leave alone whatever a write in progress keeps
 * beside them.
 */
class CoverArtLocalSourceImplTest {

    private val root: File = Files.createTempDirectory("campfire-cover-art").toFile()
    private val localSource = CoverArtLocalSourceImpl(JvmFileStorage(root), Logger.Standard)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `the prune deletes unwanted keys and nothing that is not a key`() = runBlocking {
        val firstKey = "0123456789abcdef".repeat(4)
        val secondKey = "fedcba9876543210".repeat(4)
        localSource.saveCoverArt(firstKey, byteArrayOf(1))
        localSource.saveCoverArt(secondKey, byteArrayOf(2))
        val temporaryFile = File(root, "covers/.campfire-123.tmp").apply { writeBytes(byteArrayOf(3)) }

        localSource.keepOnlyCoverArt(setOf(firstKey))

        assertContentEquals(byteArrayOf(1), localSource.loadCoverArt(firstKey))
        assertNull(localSource.loadCoverArt(secondKey))
        assertTrue(temporaryFile.exists())
    }

    @Test
    fun `an empty copy, which marks an address with no cover, loads back empty and is pruned like any other`() = runBlocking {
        localSource.saveCoverArt(KEY, ByteArray(0))

        assertContentEquals(ByteArray(0), localSource.loadCoverArt(KEY))
        localSource.keepOnlyCoverArt(emptySet())
        assertNull(localSource.loadCoverArt(KEY))
    }

    @Test
    fun `every copy written is kept out of the device backup, however often it is written`() = runBlocking {
        val fileStorage = InMemoryFileStorage()
        val localSource = CoverArtLocalSourceImpl(fileStorage, Logger.Standard)

        localSource.saveCoverArt(KEY, byteArrayOf(1))
        localSource.saveCoverArt(KEY, byteArrayOf(2))

        assertEquals(listOf(KEY), fileStorage.listNames(StorageDirectory.COVERS))
        assertEquals(emptyList(), fileStorage.namesInDeviceBackup(StorageDirectory.COVERS))
    }

    @Test
    fun `a storage that fails is answered with nothing`() = runBlocking {
        val failing = object : FileStorage by InMemoryFileStorage() {
            override suspend fun readBytes(directory: StorageDirectory, name: String): ByteArray? = throw LibraryStorageException("Unreadable")

            override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) = throw LibraryStorageException("Full")

            override suspend fun list(directory: StorageDirectory): List<StoredFileInfo> = throw LibraryStorageException("Unlistable")
        }
        val localSource = CoverArtLocalSourceImpl(failing, Logger.Standard)

        assertNull(localSource.loadCoverArt(KEY))
        localSource.saveCoverArt(KEY, byteArrayOf(1))
        assertNull(localSource.getCoverArtCacheSize())
    }

    @Test
    fun `a cancellation is not taken for a failure`() = runBlocking<Unit> {
        val cancelled = object : FileStorage by InMemoryFileStorage() {
            override suspend fun readBytes(directory: StorageDirectory, name: String): ByteArray? = throw CancellationException("The screen went away.")
        }

        assertFailsWith<CancellationException> { CoverArtLocalSourceImpl(cancelled, Logger.Standard).loadCoverArt(KEY) }
    }

    private companion object {
        val KEY = "0123456789abcdef".repeat(4)
    }
}
