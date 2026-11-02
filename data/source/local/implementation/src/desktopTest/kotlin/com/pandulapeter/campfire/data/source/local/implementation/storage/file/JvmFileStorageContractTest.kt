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

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest

class JvmFileStorageContractTest : FileStorageContractTest() {

    private val root: File = Files.createTempDirectory("campfire-file-storage").toFile()

    override fun newStorage(): FileStorage = JvmFileStorage(root)

    override fun deleteDirectoryBehindItsBack(directory: StorageDirectory) {
        root.resolve(directory.pathSegments.joinToString("/")).deleteRecursively()
    }

    override fun makeDirectoryUnderFileName(directory: StorageDirectory, name: String) {
        root.resolve(directory.pathSegments.joinToString("/")).resolve(name).mkdirs()
    }

    @AfterTest
    override fun tearDown() {
        root.deleteRecursively()
    }
}
