/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation

import com.pandulapeter.campfire.data.model.library.normalizedToNfc
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StoredFileInfo

/**
 * A [FileStorage] in memory. With [foldsNames] it behaves like APFS: a file keeps the spelling it was first written
 * with, and every question about a name is answered by its folded form, so that two spellings of one name are one
 * file. Without it every spelling is a file of its own, as on ext4.
 *
 * The device backup exclusion is kept the way iOS keeps it, as a mark on the file itself that a write drops along with
 * the file it replaces.
 */
internal class InMemoryFileStorage(private val foldsNames: Boolean = false) : FileStorage {

    private class StoredFile(val name: String, val bytes: ByteArray, val isKeptOutOfDeviceBackup: Boolean = false)

    private val files = mutableMapOf<Pair<StorageDirectory, String>, StoredFile>()
    var existsCalls = 0
    var listCalls = 0

    private fun key(directory: StorageDirectory, name: String) = directory to if (foldsNames) name.normalizedToNfc().lowercase() else name

    /** The names in [directory] that are not marked to be left out of the device backup. */
    fun namesInDeviceBackup(directory: StorageDirectory) = files.filter { (key, file) -> key.first == directory && !file.isKeptOutOfDeviceBackup }
        .map { it.value.name }

    override suspend fun list(directory: StorageDirectory): List<StoredFileInfo> = TODO()

    override suspend fun listNames(directory: StorageDirectory) = files.filterKeys { it.first == directory }.values.map { it.name }.also { listCalls++ }

    override suspend fun info(directory: StorageDirectory, name: String): StoredFileInfo? = TODO()

    override suspend fun exists(directory: StorageDirectory, name: String) = (key(directory, name) in files).also { existsCalls++ }

    override suspend fun readText(directory: StorageDirectory, name: String) = readBytes(directory, name)?.decodeToString()

    override suspend fun readBytes(directory: StorageDirectory, name: String) = files[key(directory, name)]?.bytes

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = writeBytes(directory, name, text.encodeToByteArray())

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) {
        val key = key(directory, name)
        files[key] = StoredFile(name = files[key]?.name ?: name, bytes = bytes)
    }

    override suspend fun delete(directory: StorageDirectory, name: String) {
        files -= key(directory, name)
    }

    override suspend fun keepOutOfDeviceBackup(directory: StorageDirectory, name: String) {
        val key = key(directory, name)
        files[key] = checkNotNull(files[key]) { "$name is not there to be marked." }.let { StoredFile(it.name, it.bytes, isKeptOutOfDeviceBackup = true) }
    }
}
