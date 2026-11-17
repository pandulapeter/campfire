/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * The contract run on the iOS simulator by `iosSimulatorArm64Test`, against the same Foundation calls the app makes. A
 * device target only compiles it: no test binary is ever run on a device.
 */
class IosFileStorageContractTest : FileStorageContractTest() {

    private val fileManager = NSFileManager.defaultManager
    private val root = "${NSTemporaryDirectory().trimEnd('/')}/campfire-file-storage-${Uuid.random()}"

    override fun newStorage(): FileStorage = FoundationFileStorage(logger = { }, rootPath = { root })

    override suspend fun deleteDirectoryBehindItsBack(directory: StorageDirectory) {
        fileManager.removeItemAtPath(directoryPath(directory), null)
    }

    override suspend fun makeDirectoryUnderFileName(directory: StorageDirectory, name: String) {
        fileManager.createDirectoryAtPath("${directoryPath(directory)}/$name", true, null, null)
    }

    @AfterTest
    override fun tearDown() {
        fileManager.removeItemAtPath(root, null)
    }

    @Test
    fun `a staged file moved into the library is put back into the device backup or kept out of it`() = runTest {
        val storage = newStorage()
        val bytes = byteArrayOf(1, 2, 3)
        storage.writeBytes(StorageDirectory.IMPORTS, "picture.png", bytes)
        storage.writeBytes(StorageDirectory.IMPORTS, "attachment.pdf", bytes)
        storage.keepOutOfDeviceBackup(StorageDirectory.IMPORTS, "picture.png")
        storage.keepOutOfDeviceBackup(StorageDirectory.IMPORTS, "attachment.pdf")

        storage.moveInto(StorageDirectory.IMPORTS, "picture.png", StorageDirectory.FILES, "picture.png")
        storage.moveInto(StorageDirectory.IMPORTS, "attachment.pdf", StorageDirectory.FILES, "attachment.pdf")
        storage.keepInDeviceBackup(StorageDirectory.FILES, "picture.png")
        storage.keepOutOfDeviceBackup(StorageDirectory.FILES, "attachment.pdf")

        assertContentEquals(bytes, storage.readBytes(StorageDirectory.FILES, "picture.png"))
        assertFalse(isExcludedFromBackup(StorageDirectory.FILES, "picture.png"))
        assertTrue(isExcludedFromBackup(StorageDirectory.FILES, "attachment.pdf"))
        assertEquals(emptyList(), storage.listNames(StorageDirectory.IMPORTS))
    }

    @Test
    fun `a file the app owns is taken into the imports by a move over whatever had its name`() = runTest {
        val storage = newStorage()
        val bytes = byteArrayOf(4, 5, 6, 7)
        storage.writeBytes(StorageDirectory.EXPORTS, "outside.zip", bytes)
        storage.writeBytes(StorageDirectory.IMPORTS, "archive.zip", byteArrayOf(1))
        val outside = "${directoryPath(StorageDirectory.EXPORTS)}/outside.zip"
        val progress = mutableListOf<Long>()

        storage.takeInto(StorageDirectory.IMPORTS, "archive.zip", outside, progress::add)

        assertContentEquals(bytes, storage.readBytes(StorageDirectory.IMPORTS, "archive.zip"))
        assertFalse(fileManager.fileExistsAtPath(outside))
        assertEquals(listOf(bytes.size.toLong()), progress)
    }

    @Test
    fun `a file that is not there is not taken`() = runTest {
        val storage = newStorage()

        assertFailsWith<LibraryStorageException> {
            storage.takeInto(StorageDirectory.IMPORTS, "archive.zip", "$root/missing.zip") { }
        }
        assertFalse(storage.exists(StorageDirectory.IMPORTS, "archive.zip"))
    }

    private fun isExcludedFromBackup(directory: StorageDirectory, name: String) =
        (NSURL.fileURLWithPath("${directoryPath(directory)}/$name").resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null)
            ?.get(NSURLIsExcludedFromBackupKey) as? NSNumber)?.boolValue == true

    private fun directoryPath(directory: StorageDirectory) = "$root/${directory.pathSegments.joinToString("/")}"
}
