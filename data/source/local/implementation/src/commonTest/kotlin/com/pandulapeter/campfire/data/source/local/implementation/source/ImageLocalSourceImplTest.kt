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

import com.pandulapeter.campfire.data.source.local.api.ImageShelf
import com.pandulapeter.campfire.data.source.local.implementation.InMemoryFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageLocalSourceImplTest {

    private val storage = InMemoryFileStorage()
    private val source = ImageLocalSourceImpl(storage)

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

    private companion object {
        const val PICTURE = "0123456789abcdef.png"
        const val ATTACHMENT = "3fa9c2e81b7d4f06.pdf"
        const val DRAFT_ATTACHMENT = "3fa9c2e81b7d4f07.mp3"
    }
}
