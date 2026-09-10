/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import org.koin.core.scope.Scope
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

internal actual fun Scope.createFileStorage(): FileStorage = OpfsFileStorage()

/**
 * The web [FileStorage], on top of the Origin Private File System: a sandboxed, per-origin file system that is not
 * visible to the user but behaves like a real one, so the library can have the same shape as on every other platform.
 *
 * It needs a secure context (https or `localhost`), which both the development server and the published site provide.
 *
 * The browser has a single thread, so [Dispatchers.Default] is the closest thing to `Dispatchers.IO` here; the OPFS
 * calls themselves are asynchronous anyway.
 */
private class OpfsFileStorage : FileStorage {

    override suspend fun list(directory: StorageDirectory) = withContext(Dispatchers.Default) {
        // One string instead of a handle per file: crossing the Kotlin/JS boundary for every entry would be far slower.
        listEntries(directoryHandle(directory)).await()?.toString().orEmpty()
            .split(ENTRY_SEPARATOR)
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                entry.split(FIELD_SEPARATOR).takeIf { it.size == FIELD_COUNT }?.let { fields ->
                    StoredFileInfo(
                        name = fields[0],
                        size = fields[1].toDoubleOrNull()?.toLong() ?: 0L,
                        lastModified = fields[2].toDoubleOrNull()?.toLong() ?: 0L
                    )
                }
            }
            .sortedBy { it.name }
    }

    override suspend fun info(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        fileHandle(directory, name, create = false)?.let { handle ->
            fileInfo(handle).await()?.toString()?.split(FIELD_SEPARATOR)?.takeIf { it.size == 2 }?.let { fields ->
                StoredFileInfo(
                    name = name,
                    size = fields[0].toDoubleOrNull()?.toLong() ?: 0L,
                    lastModified = fields[1].toDoubleOrNull()?.toLong() ?: 0L
                )
            }
        }
    }

    override suspend fun exists(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        fileHandle(directory, name, create = false) != null
    }

    override suspend fun readText(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        fileHandle(directory, name, create = false)?.let { readFileText(it).await()?.toString()?.withoutByteOrderMark() }
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        fileHandle(directory, name, create = false)?.let { readFileBytes(it).await()?.toByteArray() }
    }

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = withContext(Dispatchers.Default) {
        writeFileText(requireFileHandle(directory, name), text).await()
        Unit
    }

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) = withContext(Dispatchers.Default) {
        writeFileBytes(requireFileHandle(directory, name), bytes.toInt8Array()).await()
        Unit
    }

    override suspend fun delete(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        requireValidFileName(name)
        removeEntry(directoryHandle(directory), name).await()
        Unit
    }

    private suspend fun requireFileHandle(directory: StorageDirectory, name: String) =
        fileHandle(directory, name, create = true) ?: throw IllegalStateException("Could not create \"$name\".")

    private suspend fun fileHandle(directory: StorageDirectory, name: String, create: Boolean): JsAny? {
        requireValidFileName(name)
        return getFileHandle(directoryHandle(directory), name, create).await()
    }

    /**
     * The three directory handles, resolved once. Walking down from the root is three promises, and doing that for
     * every one of the reads of a library scan (which already run in parallel) tripled the number of calls into
     * OPFS. Nothing outside the page can remove a directory from the origin private file system, so a handle that
     * was resolved once stays valid.
     */
    private val directoryHandles = mutableMapOf<StorageDirectory, JsAny>()
    private val directoryHandlesMutex = Mutex()

    private suspend fun directoryHandle(directory: StorageDirectory): JsAny = directoryHandles[directory] ?: directoryHandlesMutex.withLock {
        directoryHandles.getOrPut(directory) {
            if (!isOpfsAvailable()) {
                throw IllegalStateException(OPFS_UNAVAILABLE)
            }
            var handle = opfsRoot().await() ?: throw IllegalStateException(OPFS_UNAVAILABLE)
            directory.pathSegments.forEach { segment ->
                handle = getDirectoryHandle(handle, segment).await() ?: throw IllegalStateException(OPFS_UNAVAILABLE)
            }
            handle
        }
    }

    private companion object {

        const val OPFS_UNAVAILABLE = "OPFS unavailable"
        const val FIELD_COUNT = 3

        // Control characters, because they are the only thing a file name is guaranteed not to contain.
        val ENTRY_SEPARATOR = Char(ENTRY_SEPARATOR_CODE).toString()
        val FIELD_SEPARATOR = Char(FIELD_SEPARATOR_CODE).toString()
    }
}

private fun isOpfsAvailable(): Boolean =
    js("typeof navigator !== 'undefined' && navigator.storage != null && typeof navigator.storage.getDirectory === 'function'")

private fun opfsRoot(): Promise<JsAny?> = js("navigator.storage.getDirectory()")

private fun getDirectoryHandle(parent: JsAny, name: String): Promise<JsAny?> = js("parent.getDirectoryHandle(name, { create: true })")

/** Resolves to `null` instead of rejecting with a `NotFoundError` when the file is not there. */
private fun getFileHandle(parent: JsAny, name: String, create: Boolean): Promise<JsAny?> =
    js("parent.getFileHandle(name, { create: create }).catch(function () { return null; })")

/** Every file of the directory as name, size and last modification time, separated by control characters. */
private fun listEntries(directory: JsAny): Promise<JsString?> = js(
    """(async function () {
        var field = String.fromCharCode(0);
        var entries = [];
        for await (var entry of directory.entries()) {
            if (entry[1].kind === 'file') {
                var file = await entry[1].getFile();
                entries.push(entry[0] + field + file.size + field + file.lastModified);
            }
        }
        return entries.join(String.fromCharCode(1));
    })()"""
)

/** The size and the last modification time of one file, separated by the same control character `listEntries` uses. */
private fun fileInfo(handle: JsAny): Promise<JsString?> =
    js("handle.getFile().then(function (file) { return file.size + String.fromCharCode(0) + file.lastModified; })")

private fun readFileText(handle: JsAny): Promise<JsString?> = js("handle.getFile().then(function (file) { return file.text(); })")

private fun readFileBytes(handle: JsAny): Promise<Int8Array?> =
    js("handle.getFile().then(function (file) { return file.arrayBuffer(); }).then(function (buffer) { return new Int8Array(buffer); })")

private fun writeFileText(handle: JsAny, text: String): Promise<JsAny?> =
    js("handle.createWritable().then(function (writable) { return writable.write(text).then(function () { return writable.close(); }); })")

private fun writeFileBytes(handle: JsAny, bytes: Int8Array): Promise<JsAny?> =
    js("handle.createWritable().then(function (writable) { return writable.write(bytes).then(function () { return writable.close(); }); })")

/** Resolves instead of rejecting when the file is not there, which makes deleting a missing file a no-op. */
private fun removeEntry(parent: JsAny, name: String): Promise<JsAny?> = js("parent.removeEntry(name).catch(function () { return null; })")

private const val FIELD_SEPARATOR_CODE = 0
private const val ENTRY_SEPARATOR_CODE = 1
