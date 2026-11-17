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
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.model.library.ExportSink
import com.pandulapeter.campfire.data.source.local.api.ImageShelf
import com.pandulapeter.campfire.data.source.local.implementation.source.ArchiveLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.ImageLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.staging.ImportStaging
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.Deflater
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a phone's heap must survive with the largest files a library holds, run by `largeFileMemoryTest` in a JVM capped
 * at 128 MB, less than three copies of one 50 MiB attachment: every file is made on disk a slice at a time, so whatever
 * is held whole is the code's under test, and an archive is unpacked from the staging folder. Left out of `desktopTest`,
 * whose heap would hide what this is for.
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

        val packed = ArchiveLocalSourceImpl(logger = Logger.Standard, fileStorage = storage, importStaging = ImportStaging(storage, Logger.Standard)).pack(
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

    @Test
    fun `an archive nested in a staged one and larger than the heap is staged and unpacked with its attachments a slice at a time`() = runBlocking {
        val staging = ImportStaging(storage, Logger.Standard)
        val names = List(3) { "0123456789abcde$it.mp3" }
        val song = "{title: B}\n" + names.joinToString("") { "{meta: attachment $it Take}\n" }
        // Written by the JDK, a slice at a time, without compression, so that the nested archive is as large as what it holds.
        val inner = File(root, "inner.zip")
        ZipOutputStream(inner.outputStream().buffered()).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            zip.putNextEntry(java.util.zip.ZipEntry("b.cho"))
            zip.write(song.encodeToByteArray())
            names.forEachIndexed { index, name ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.writeNoise(seed = index, size = NESTED_ATTACHMENT_SIZE)
            }
        }
        val outer = File(root, "outer.zip")
        ZipOutputStream(outer.outputStream().buffered()).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            zip.putNextEntry(java.util.zip.ZipEntry("nested/library.zip"))
            inner.inputStream().use { it.copyTo(zip, SLICE_SIZE) }
        }
        inner.delete()
        val sink = staging.openArchive(staging.newRequest(), announcedSize = outer.length())
        outer.inputStream().use { input ->
            val slice = ByteArray(SLICE_SIZE)
            while (true) {
                val read = input.read(slice).takeIf { it >= 0 } ?: break
                sink.write(slice, 0, read)
            }
        }
        val archive = sink.finish()
        outer.delete()

        val unpacked = ArchiveLocalSourceImpl(logger = Logger.Standard, fileStorage = storage, importStaging = staging)
            .unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        val attachments = unpacked.files.single().images.map { (it.content as IncomingContent.Staged).file }
        assertEquals(List(3) { NESTED_ATTACHMENT_SIZE.toLong() }, attachments.map { it.size })
        assertEquals((attachments.map { it.name } + archive.name).sorted(), storage.listNames(StorageDirectory.IMPORTS).sorted())
    }

    private fun OutputStream.writeNoise(seed: Int, size: Int) {
        val random = Random(seed)
        val slice = ByteArray(SLICE_SIZE)
        repeat(size / slice.size) {
            random.nextBytes(slice)
            write(slice)
        }
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

        /** Three of them, nested, are more than the heap holds: neither the nested archive nor they can be held whole. */
        const val NESTED_ATTACHMENT_SIZE = 45 * 1024 * 1024
        const val SLICE_SIZE = 1024 * 1024
    }
}
