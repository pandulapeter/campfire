/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.sync.implementation.run.SyncEngine
import com.pandulapeter.campfire.data.model.library.LibraryFile
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource

/**
 * The library held in memory, for running [SyncEngine] against. [onRead] runs before a read answers, which is where a
 * test makes a file that is there impossible to read, and [onWrite] before a write is stored, with the name it chose,
 * which is where a test makes the disk full, and [onDelete] before a deletion. The last two may suspend, which is
 * where a test lets the app write a file in the middle of the engine's own change to it. [canHoldFileName] is where a
 * test makes a Windows PC, and [naming] how the platform's file system compares names: [files] is the directory as it
 * lists, and every call finds a name the way that file system would. What the real source answers on top of the file
 * system is answered the way `LibraryFileLocalSourceImpl` and `uniqueName` answer it: a free name, and a numbered
 * variant of one, under any case or Unicode form, and an existing spelling as the file system's own check finds it.
 */
internal class FakeLibraryFileLocalSource(
    files: Map<SyncKey, ByteArray> = emptyMap(),
    var onRead: (SyncKey) -> Unit = {},
    var onWrite: suspend (SyncKey) -> Unit = {},
    var onDelete: suspend (SyncKey) -> Unit = {},
    private val canHoldFileName: (String) -> Boolean = { true },
    val naming: FileSystemNaming = FileSystemNaming.EXACT,
) : LibraryFileLocalSource {

    val files = files.toMutableMap()

    /** The key [kind] / [name] finds on this file system, or null for a name no file answers to. */
    fun held(kind: LibraryFileKind, name: String) = SyncKey(kind = kind, name = name).takeIf { it in files }
        ?: naming.key(name).let { key -> files.keys.firstOrNull { it.kind == kind && naming.key(it.name) == key } }

    /** What a save does: the file [key] finds is replaced, under the spelling the file system leaves it, or created. */
    fun put(key: SyncKey, bytes: ByteArray): ByteArray? {
        val held = held(key.kind, key.name)
        val previous = held?.let(files::remove)
        files[if (held == null || naming.takesWrittenSpelling) key.copy(name = naming.stored(key.name)) else held] = bytes
        return previous
    }

    /** What a deletion does: removes the file [key] finds. */
    fun remove(key: SyncKey) = held(key.kind, key.name)?.let(files::remove)

    override suspend fun loadLibraryFiles() = files.filterKeys { it.kind.matches(it.name) }.map { (key, bytes) ->
        LibraryFile(kind = key.kind, name = key.name, size = bytes.size.toLong(), lastModified = 0)
    }

    override suspend fun readLibraryFile(kind: LibraryFileKind, name: String): ByteArray? {
        val key = SyncKey(kind = kind, name = name)
        onRead(key)
        return held(kind, name)?.let(files::get)
    }

    override suspend fun existingSpellingOf(kind: LibraryFileKind, name: String) = if (held(kind, name) != null) {
        name
    } else {
        files.keys.firstOrNull { it.kind == kind && LibraryFiles.isSameLibraryName(it.name, name) }?.name
    }

    override suspend fun writeLibraryFile(kind: LibraryFileKind, name: String, bytes: ByteArray) {
        val key = SyncKey(kind = kind, name = name)
        onWrite(key)
        put(key, bytes)
    }

    override suspend fun writeLibraryFileToFreeName(
        kind: LibraryFileKind,
        desiredName: String,
        bytes: ByteArray,
        isTaken: (name: String) -> Boolean,
    ): String {
        val taken = files.keys.filter { it.kind == kind }.mapTo(mutableSetOf()) { LibraryFiles.identityKey(it.name) }
        fun isFree(candidate: String) = LibraryFiles.identityKey(candidate) !in taken && !isTaken(candidate) && held(kind, candidate) == null
        val extension = desiredName.extension()
        val base = desiredName.removeSuffix(extension)
        val name = (sequenceOf(desiredName) + generateSequence(2) { it + 1 }.map { "$base ($it)$extension" }).first(::isFree)
        val key = SyncKey(kind = kind, name = name)
        onWrite(key)
        put(key, bytes)
        return name
    }

    /** The variants [writeLibraryFileToFreeName] names, under any case or Unicode form, as the real storage finds them. */
    override suspend fun numberedVariantHolding(kind: LibraryFileKind, name: String, bytes: ByteArray): String? {
        val extension = LibraryFiles.identityKey(name.extension())
        val base = LibraryFiles.identityKey(name.removeSuffix(name.extension()))
        val variant = Regex("""${Regex.escape(base)} \((\d+)\)${Regex.escape(extension)}""")
        return files.entries
            .filter { (key, _) -> key.kind == kind }
            .mapNotNull { (key, stored) ->
                variant.matchEntire(LibraryFiles.identityKey(key.name))?.let { it.groupValues[1].toInt() to (key.name to stored) }
            }
            .filter { (number, _) -> number >= 2 }
            .sortedBy { (number, _) -> number }
            .firstOrNull { (_, file) -> file.second.contentEquals(bytes) }
            ?.second
            ?.first
    }

    override suspend fun deleteLibraryFile(kind: LibraryFileKind, name: String) {
        val key = SyncKey(kind = kind, name = name)
        onDelete(key)
        remove(key)
    }

    override fun canHoldFileName(kind: LibraryFileKind, name: String) = canHoldFileName(name)

    /** ".setlist.json" before the part after the last dot, as the real storage reads an extension. */
    private fun String.extension() = when {
        endsWith(".setlist.json", ignoreCase = true) -> takeLast(".setlist.json".length)
        contains('.') -> substring(lastIndexOf('.'))
        else -> ""
    }
}
