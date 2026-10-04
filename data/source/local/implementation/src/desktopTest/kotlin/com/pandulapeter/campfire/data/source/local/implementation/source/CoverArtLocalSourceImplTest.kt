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

import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The prune runs while thumbnails are being saved, so it must leave alone whatever a write in progress keeps beside them. */
class CoverArtLocalSourceImplTest {

    private val root: File = Files.createTempDirectory("campfire-cover-art").toFile()
    private val localSource = CoverArtLocalSourceImpl(JvmFileStorage(root))

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
}
