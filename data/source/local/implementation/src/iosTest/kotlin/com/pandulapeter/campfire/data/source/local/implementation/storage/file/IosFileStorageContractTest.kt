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
    fun `a marked draft picture copied into the library is in the device backup`() = runTest {
        val storage = newStorage()
        storage.writeBytes(StorageDirectory.DRAFT_IMAGES, PICTURE, byteArrayOf(1, 2, 3))
        setExcludedFromBackup("${directoryPath(StorageDirectory.DRAFT_IMAGES)}/$PICTURE")

        storage.copyInto(from = StorageDirectory.DRAFT_IMAGES, fromName = PICTURE, to = StorageDirectory.FILES, toName = PICTURE)

        assertFalse(isExcludedFromBackup(StorageDirectory.FILES, PICTURE))
    }

    @Test
    fun `a marked staged file moved into the library is in the device backup as a picture and out of it as an attachment`() = runTest {
        val storage = newStorage()
        val bytes = byteArrayOf(1, 2, 3)
        storage.writeBytes(StorageDirectory.IMPORTS, PICTURE, bytes)
        storage.writeBytes(StorageDirectory.IMPORTS, ATTACHMENT, bytes)
        setExcludedFromBackup("${directoryPath(StorageDirectory.IMPORTS)}/$PICTURE")

        storage.moveInto(StorageDirectory.IMPORTS, PICTURE, StorageDirectory.FILES, PICTURE)
        storage.moveInto(StorageDirectory.IMPORTS, ATTACHMENT, StorageDirectory.FILES, ATTACHMENT)

        assertContentEquals(bytes, storage.readBytes(StorageDirectory.FILES, PICTURE))
        assertFalse(isExcludedFromBackup(StorageDirectory.FILES, PICTURE))
        assertTrue(isExcludedFromBackup(StorageDirectory.FILES, ATTACHMENT))
        assertEquals(emptyList(), storage.listNames(StorageDirectory.IMPORTS))
    }

    @Test
    fun `an attachment written or copied or moved into the library is out of the device backup`() = runTest {
        val storage = newStorage()
        storage.writeBytes(StorageDirectory.FILES, ATTACHMENT, byteArrayOf(1))
        storage.writeBytes(StorageDirectory.DRAFT_IMAGES, OTHER_ATTACHMENT, byteArrayOf(2))
        storage.writeBytes(StorageDirectory.IMPORTS, THIRD_ATTACHMENT, byteArrayOf(3))

        storage.copyInto(from = StorageDirectory.DRAFT_IMAGES, fromName = OTHER_ATTACHMENT, to = StorageDirectory.FILES, toName = OTHER_ATTACHMENT)
        storage.moveInto(StorageDirectory.IMPORTS, THIRD_ATTACHMENT, StorageDirectory.FILES, THIRD_ATTACHMENT)

        listOf(ATTACHMENT, OTHER_ATTACHMENT, THIRD_ATTACHMENT).forEach { assertTrue(isExcludedFromBackup(StorageDirectory.FILES, it), it) }
    }

    @Test
    fun `of the preferences only the preferences document is in the device backup`() = runTest {
        val storage = newStorage()
        val others = listOf("preferences.json.bad", "sync-index.json", "sync-credentials-forget-pending", "shared-links.json", "shared-links-journal.json", "editor-draft.json")
        (others + PREFERENCES_DOCUMENT_NAME).forEach { storage.writeText(StorageDirectory.PREFERENCES, it, "{}") }

        assertFalse(isExcludedFromBackup(StorageDirectory.PREFERENCES, PREFERENCES_DOCUMENT_NAME))
        others.forEach { assertTrue(isExcludedFromBackup(StorageDirectory.PREFERENCES, it), it) }
    }

    @Test
    fun `the covers and the draft pictures and the imports are out of the device backup as folders even made before the storage`() = runTest {
        val folders = listOf(StorageDirectory.COVERS, StorageDirectory.DRAFT_IMAGES, StorageDirectory.IMPORTS)
        folders.forEach { fileManager.createDirectoryAtPath(directoryPath(it), true, null, null) }
        val storage = newStorage()

        folders.forEach { storage.listNames(it) }

        folders.forEach { assertTrue(isExcludedFromBackup(directoryPath(it)), "$it") }
    }

    @Test
    fun `the first use of the library's files puts a marked picture back into the device backup and leaves an attachment out`() = runTest {
        fileManager.createDirectoryAtPath(directoryPath(StorageDirectory.FILES), true, null, null)
        val picture = "${directoryPath(StorageDirectory.FILES)}/$PICTURE"
        fileManager.createFileAtPath(picture, null, null)
        fileManager.createFileAtPath("${directoryPath(StorageDirectory.FILES)}/$ATTACHMENT", null, null)
        setExcludedFromBackup(picture)
        val storage = newStorage()

        storage.exists(StorageDirectory.FILES, PICTURE)

        assertFalse(isExcludedFromBackup(StorageDirectory.FILES, PICTURE))
        assertTrue(isExcludedFromBackup(StorageDirectory.FILES, ATTACHMENT))
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

    /**
     * Asked only of a file in the library's files or the preferences, or of a folder: a file inside a folder kept out of
     * the backup reads as kept out too, by inheritance, whatever it carries itself.
     */
    private fun isExcludedFromBackup(directory: StorageDirectory, name: String) = isExcludedFromBackup("${directoryPath(directory)}/$name")

    private fun isExcludedFromBackup(path: String) = (NSURL.fileURLWithPath(path).resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null)
        ?.get(NSURLIsExcludedFromBackupKey) as? NSNumber)?.boolValue == true

    /** Marks a file as an older version of the app did, behind the storage's back. */
    private fun setExcludedFromBackup(path: String) {
        assertTrue(NSURL.fileURLWithPath(path).setResourceValue(true, NSURLIsExcludedFromBackupKey, null))
    }

    private fun directoryPath(directory: StorageDirectory) = "$root/${directory.pathSegments.joinToString("/")}"

    private companion object {
        const val PICTURE = "0123456789abcdef.png"
        const val ATTACHMENT = "3fa9c2e81b7d4f06.pdf"
        const val OTHER_ATTACHMENT = "3fa9c2e81b7d4f07.mp3"
        const val THIRD_ATTACHMENT = "3fa9c2e81b7d4f08.mov"
    }
}
