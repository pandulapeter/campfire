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

import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * A [JvmFileStorage] over [root] whose whole-file writes are not flushed to the device before they replace their file,
 * for tests that write thousands of small libraries and test no power loss: the flush is most of what such a write
 * costs. Still atomic, through a temporary file of the name [JvmFileStorage] hides from its listings.
 */
internal class UnflushedFileStorage(root: File, private val delegate: FileStorage = JvmFileStorage(root)) : FileStorage by delegate {

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) = write(directory, name) { Files.write(it, bytes) }

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = write(directory, name) { Files.writeString(it, text) }

    private suspend fun write(directory: StorageDirectory, name: String, write: (Path) -> Unit) = withContext(Dispatchers.IO) {
        require(canHoldFileName(name)) { "Invalid file name: \"$name\"." }
        val target = Path.of(delegate.pathOf(directory, name))
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, TEMPORARY_FILE_PREFIX, TEMPORARY_FILE_SUFFIX)
        try {
            write(temporary)
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
        Unit
    }

    private companion object {
        const val TEMPORARY_FILE_PREFIX = ".campfire-"
        const val TEMPORARY_FILE_SUFFIX = ".tmp"
    }
}
