/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.LibraryFile
import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource

/**
 * The library held in memory, for running [SyncEngine] against. [onRead] runs before a read answers, which is where a
 * test makes a file that is there impossible to read, and [onWrite] before a write is stored, with the name it chose,
 * which is where a test makes the disk full, and [onDelete] before a deletion. The last two may suspend, which is
where a test lets the app write a file in the middle of the engine's own change to it. [canHoldFileName] is where a
test makes a Windows PC.
 */
internal class FakeLibraryFileLocalSource(
    files: Map<SyncKey, ByteArray> = emptyMap(),
    var onRead: (SyncKey) -> Unit = {},
    var onWrite: suspend (SyncKey) -> Unit = {},
    var onDelete: suspend (SyncKey) -> Unit = {},
    private val canHoldFileName: (String) -> Boolean = { true },
) : LibraryFileLocalSource {

    val files = files.toMutableMap()

    override suspend fun loadLibraryFiles() = files.filterKeys { it.kind.matches(it.name) }.map { (key, bytes) ->
        LibraryFile(kind = key.kind, name = key.name, size = bytes.size.toLong(), lastModified = 0)
    }

    override suspend fun readLibraryFile(kind: LibraryFileKind, name: String): ByteArray? {
        val key = SyncKey(kind = kind, name = name)
        onRead(key)
        return files[key]
    }

    override suspend fun writeLibraryFile(kind: LibraryFileKind, name: String, bytes: ByteArray) {
        val key = SyncKey(kind = kind, name = name)
        onWrite(key)
        files[key] = bytes
    }

    override suspend fun writeLibraryFileToFreeName(
        kind: LibraryFileKind,
        desiredName: String,
        bytes: ByteArray,
        isTaken: (name: String) -> Boolean,
    ): String {
        val extension = desiredName.substringAfter('.', "")
        val base = desiredName.substringBefore('.')
        val name = generateSequence(2) { it + 1 }
            .map { "$base ($it).$extension" }
            .first { SyncKey(kind = kind, name = it) !in files && !isTaken(it) }
        val key = SyncKey(kind = kind, name = name)
        onWrite(key)
        files[key] = bytes
        return name
    }

    override suspend fun deleteLibraryFile(kind: LibraryFileKind, name: String) {
        val key = SyncKey(kind = kind, name = name)
        onDelete(key)
        files -= key
    }

    override fun canHoldFileName(kind: LibraryFileKind, name: String) = canHoldFileName(name)
}
