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
import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.source.local.api.ImageShelf
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.InMemoryFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.staging.ImportStaging
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageLocalSourceImplTest {

    private val storage = InMemoryFileStorage()
    private val staging = ImportStaging(storage, Logger.Standard)
    private val source = ImageLocalSourceImpl(storage, staging)

    @Test
    fun `the library's attachments are kept out of the device backup and its pictures are not`() = runTest {
        source.writeImage(ImageShelf.LIBRARY, PICTURE, byteArrayOf(1))
        source.writeImage(ImageShelf.LIBRARY, ATTACHMENT, byteArrayOf(2, 3))
        source.writeImage(ImageShelf.DRAFT, DRAFT_ATTACHMENT, byteArrayOf(4))

        assertEquals(listOf(PICTURE), storage.namesInDeviceBackup(StorageDirectory.FILES))
        assertEquals(emptyList(), storage.namesInDeviceBackup(StorageDirectory.DRAFT_IMAGES))
    }

    @Test
    fun `pictures and attachments are listed with their sizes and nothing else in the folder is`() = runTest {
        source.writeImage(ImageShelf.LIBRARY, PICTURE, byteArrayOf(1))
        source.writeImage(ImageShelf.LIBRARY, ATTACHMENT, byteArrayOf(2, 3))
        storage.writeBytes(StorageDirectory.FILES, "notes.pdf", byteArrayOf(5))

        assertEquals(setOf(PICTURE, ATTACHMENT), source.listImageNames(ImageShelf.LIBRARY).toSet())
        assertEquals(mapOf(PICTURE to 1L, ATTACHMENT to 2L), source.listImageSizes(ImageShelf.LIBRARY))
    }

    @Test
    fun `a draft picture copied into the library reaches the device backup and an attachment stays out of it`() = runTest {
        source.writeImage(ImageShelf.DRAFT, PICTURE, byteArrayOf(1))
        source.writeImage(ImageShelf.DRAFT, ATTACHMENT, byteArrayOf(2, 3))

        source.copyImage(ImageShelf.DRAFT, PICTURE, ImageShelf.LIBRARY, PICTURE)
        source.copyImage(ImageShelf.DRAFT, ATTACHMENT, ImageShelf.LIBRARY, ATTACHMENT)

        assertEquals(listOf(PICTURE), storage.namesInDeviceBackup(StorageDirectory.FILES))
        assertEquals(setOf(PICTURE, ATTACHMENT), source.listImageNames(ImageShelf.DRAFT).toSet())
        assertEquals(emptyList(), storage.namesInDeviceBackup(StorageDirectory.DRAFT_IMAGES))
    }

    @Test
    fun `a file is hashed as its bytes are and a missing one is not`() = runTest {
        val bytes = ByteArray(600_000) { (it % 253).toByte() }
        source.writeImage(ImageShelf.LIBRARY, ATTACHMENT, bytes)

        assertEquals(Sha256.hashToHex(bytes), source.hashImage(ImageShelf.LIBRARY, ATTACHMENT))
        assertNull(source.hashImage(ImageShelf.LIBRARY, PICTURE))
    }

    @Test
    fun `a file holds the same bytes compared chunk by chunk and not bytes of another size`() = runTest {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        source.writeImage(ImageShelf.LIBRARY, ATTACHMENT, bytes)
        val staged = stage(bytes)

        assertTrue(source.holdsContent(ImageShelf.LIBRARY, ATTACHMENT, IncomingContent.Bytes(bytes.copyOf())))
        assertTrue(source.holdsContent(ImageShelf.LIBRARY, ATTACHMENT, IncomingContent.Staged(staged)))
        val shorter = bytes.copyOf(bytes.size - 1)
        assertFalse(source.holdsContent(ImageShelf.LIBRARY, ATTACHMENT, IncomingContent.Bytes(shorter)))
        assertFalse(source.holdsContent(ImageShelf.LIBRARY, ATTACHMENT, IncomingContent.Staged(stage(shorter))))
        assertFalse(source.holdsContent(ImageShelf.LIBRARY, PICTURE, IncomingContent.Bytes(bytes)))
    }

    @Test
    fun `a file of the same size whose last chunk differs does not hold the same bytes`() = runTest {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        source.writeImage(ImageShelf.LIBRARY, ATTACHMENT, bytes)
        val other = bytes.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }

        assertFalse(source.holdsContent(ImageShelf.LIBRARY, ATTACHMENT, IncomingContent.Bytes(other)))
        assertFalse(source.holdsContent(ImageShelf.LIBRARY, ATTACHMENT, IncomingContent.Staged(stage(other))))
    }

    @Test
    fun `a staged picture moved into the library is in the device backup and an attachment is not`() = runTest {
        val picture = stage(byteArrayOf(1, 2, 3), ".png")
        val attachment = stage(byteArrayOf(4, 5), ".pdf")
        storage.keepOutOfDeviceBackup(StorageDirectory.IMPORTS, picture.name)
        storage.keepOutOfDeviceBackup(StorageDirectory.IMPORTS, attachment.name)

        source.moveStagedFile(picture, ImageShelf.LIBRARY, PICTURE)
        source.moveStagedFile(attachment, ImageShelf.LIBRARY, ATTACHMENT)

        assertContentEquals(byteArrayOf(1, 2, 3), source.readImage(ImageShelf.LIBRARY, PICTURE))
        assertContentEquals(byteArrayOf(4, 5), source.readImage(ImageShelf.LIBRARY, ATTACHMENT))
        assertEquals(listOf(PICTURE), storage.namesInDeviceBackup(StorageDirectory.FILES))
        assertEquals(emptyList(), storage.listNames(StorageDirectory.IMPORTS))
    }

    @Test
    fun `a staged file whose name the library holds is not moved over it and its staged copy goes`() = runTest {
        source.writeImage(ImageShelf.LIBRARY, PICTURE, byteArrayOf(1, 2, 3))
        val staged = stage(byteArrayOf(1, 2, 3), ".png")

        source.moveStagedFile(staged, ImageShelf.LIBRARY, PICTURE)

        assertContentEquals(byteArrayOf(1, 2, 3), source.readImage(ImageShelf.LIBRARY, PICTURE))
        assertEquals(emptyList(), storage.listNames(StorageDirectory.IMPORTS))
    }

    @Test
    fun `a staged file brought under a second name is copied from where the first move put it`() = runTest {
        val staged = stage(byteArrayOf(1, 2, 3), ".png")

        source.moveStagedFile(staged, ImageShelf.LIBRARY, PICTURE)
        source.moveStagedFile(staged, ImageShelf.LIBRARY, OTHER_PICTURE)

        assertContentEquals(byteArrayOf(1, 2, 3), source.readImage(ImageShelf.LIBRARY, PICTURE))
        assertContentEquals(byteArrayOf(1, 2, 3), source.readImage(ImageShelf.LIBRARY, OTHER_PICTURE))
        assertContentEquals(byteArrayOf(1, 2), source.readStagedHead(staged, length = 2))
        assertTrue(staging.isIntact(staged))
    }

    @Test
    fun `a staged file that is gone is neither read nor moved and nothing is written`() = runTest {
        val staged = stage(byteArrayOf(1, 2, 3), ".png")
        storage.delete(StorageDirectory.IMPORTS, staged.name)

        assertNull(source.readStagedHead(staged, length = 2))
        assertFalse(staging.isIntact(staged))
        assertFailsWith<LibraryStorageException> { source.moveStagedFile(staged, ImageShelf.LIBRARY, PICTURE) }
        assertEquals(emptyList(), source.listImageNames(ImageShelf.LIBRARY))
    }

    @Test
    fun `the head of a staged file is its first bytes and all of a shorter one`() = runTest {
        val staged = stage(ByteArray(100_000) { (it % 7).toByte() })

        assertContentEquals(ByteArray(10) { (it % 7).toByte() }, source.readStagedHead(staged, length = 10))
        assertEquals(100_000, source.readStagedHead(staged, length = 200_000)?.size)
    }

    private suspend fun stage(bytes: ByteArray, extension: String = ".pdf"): StagedFile {
        val sink = staging.openUnpacked(staging.newRequest(), extension, announcedSize = bytes.size.toLong())
        sink.write(bytes, 0, bytes.size)
        return sink.finish()
    }

    private companion object {
        const val PICTURE = "0123456789abcdef.png"
        const val OTHER_PICTURE = "0123456789abcdef0.png"
        const val ATTACHMENT = "3fa9c2e81b7d4f06.pdf"
        const val DRAFT_ATTACHMENT = "3fa9c2e81b7d4f07.mp3"
    }
}
