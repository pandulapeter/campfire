/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.memory

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.model.library.ExportSink
import com.pandulapeter.campfire.data.source.local.api.ImageShelf
import com.pandulapeter.campfire.data.source.local.implementation.source.ArchiveLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.ImageLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a phone's heap must survive with the largest files a library holds, run by `largeFileMemoryTest` in a JVM capped
 * at 128 MB, less than three copies of one 50 MiB attachment: every file is made on disk a slice at a time, so whatever
 * is held whole is the code's under test. Left out of `desktopTest`, whose heap would hide what this is for.
 */
class LargeFileMemoryTest {

    private val root: File = Files.createTempDirectory("campfire-large-files").toFile()
    private val storage = JvmFileStorage(root)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a library with two 50 MiB attachments that do not compress exports into a file`() = runBlocking {
        val names = listOf("0123456789abcdef.mp3", "fedcba9876543210.mp3")
        names.forEachIndexed { index, name -> storage.writeNoise(StorageDirectory.FILES, name, seed = index) }
        val sink = storage.openSink(StorageDirectory.EXPORTS, "library.zip")
        val exportSink = object : ExportSink {
            override val position get() = sink.position

            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = sink.write(bytes, offset, length)
        }

        val packed = ArchiveLocalSourceImpl(logger = Logger.Standard).pack(
            entries = names.map { name -> { storage.readBytes(StorageDirectory.FILES, name)?.let { ArchiveEntry(name = "files/$name", bytes = it) } } },
            isDated = false,
            sink = exportSink,
        )
        sink.close()

        assertEquals(2, packed.entryCount)
        assertTrue(storage.info(StorageDirectory.EXPORTS, "library.zip")!!.size > 2L * FILE_SIZE)
    }

    @Test
    fun `a 50 MiB draft file is hashed and copied into the library`() = runBlocking {
        val name = "0123456789abcdef.mp3"
        storage.writeNoise(StorageDirectory.DRAFT_IMAGES, name, seed = 3)
        val source = ImageLocalSourceImpl(storage)

        val hash = source.hashImage(ImageShelf.DRAFT, name)
        source.copyImage(from = ImageShelf.DRAFT, name = name, to = ImageShelf.LIBRARY, toName = name)

        assertEquals(hash, source.hashImage(ImageShelf.LIBRARY, name))
        assertEquals(FILE_SIZE.toLong(), source.imageSize(ImageShelf.LIBRARY, name))
    }

    private suspend fun FileStorage.writeNoise(directory: StorageDirectory, name: String, seed: Int) {
        val random = Random(seed)
        val sink = openSink(directory, name)
        val slice = ByteArray(1024 * 1024)
        repeat(FILE_SIZE / slice.size) {
            random.nextBytes(slice)
            sink.write(slice, 0, slice.size)
        }
        sink.close()
    }

    private companion object {
        const val FILE_SIZE = 50 * 1024 * 1024
    }
}
