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

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.AfterTest
import kotlin.uuid.Uuid

/**
 * The contract run on the iOS simulator by `iosSimulatorArm64Test`, against the same Foundation calls the app makes. A
 * device target only compiles it: no test binary is ever run on a device.
 */
class IosFileStorageContractTest : FileStorageContractTest() {

    private val fileManager = NSFileManager.defaultManager
    private val root = "${NSTemporaryDirectory().trimEnd('/')}/campfire-file-storage-${Uuid.random()}"

    override fun newStorage(): FileStorage = FoundationFileStorage(logger = { }, rootPath = { root })

    override fun deleteDirectoryBehindItsBack(directory: StorageDirectory) {
        fileManager.removeItemAtPath(directoryPath(directory), null)
    }

    override fun makeDirectoryUnderFileName(directory: StorageDirectory, name: String) {
        fileManager.createDirectoryAtPath("${directoryPath(directory)}/$name", true, null, null)
    }

    @AfterTest
    override fun tearDown() {
        fileManager.removeItemAtPath(root, null)
    }

    private fun directoryPath(directory: StorageDirectory) = "$root/${directory.pathSegments.joinToString("/")}"
}
