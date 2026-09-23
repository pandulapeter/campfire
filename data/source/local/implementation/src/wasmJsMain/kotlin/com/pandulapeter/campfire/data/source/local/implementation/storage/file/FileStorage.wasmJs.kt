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

import com.pandulapeter.campfire.data.model.domain.decodeLibraryText
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import org.koin.core.annotation.Single
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.js.toJsString

/**
 * The web [FileStorage], on top of the Origin Private File System: a sandboxed, per-origin file system that is not
 * visible to the user but behaves like a real one, so the library can have the same shape as on every other platform.
 *
 * It needs a secure context (https or `localhost`), which both the development server and the published site provide.
 *
 * The browser has a single thread, so [Dispatchers.Default] is the closest thing to `Dispatchers.IO` here; the OPFS
 * calls themselves are asynchronous anyway.
 */
@Single
internal class OpfsFileStorage : FileStorage {

    override suspend fun list(directory: StorageDirectory) = withContext(Dispatchers.Default) {
        failingAsStorage(directory.displayName) {
            // One string instead of a handle per file: crossing the Kotlin/JS boundary for every entry would be far slower.
            listEntries(directoryHandle(directory)).await()?.toString().orEmpty()
        }
            .split(ENTRY_SEPARATOR)
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                entry.split(FIELD_SEPARATOR).takeIf { it.size == FIELD_COUNT }?.let { fields ->
                    StoredFileInfo(
                        name = fields[0],
                        size = fields[1].toDoubleOrNull()?.toLong() ?: 0L,
                        lastModified = fields[2].toDoubleOrNull()?.toLong() ?: 0L,
                    )
                }
            }
            .sortedBy { it.name }
    }

    override suspend fun listNames(directory: StorageDirectory) = withContext(Dispatchers.Default) {
        failingAsStorage(directory.displayName) { listEntryNames(directoryHandle(directory)).await()?.toString().orEmpty() }
            .split(ENTRY_SEPARATOR).filter { it.isNotEmpty() }
    }

    override suspend fun info(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        fileHandle(directory, name)?.let { handle ->
            fileInfo(handle).await()?.toString()?.split(FIELD_SEPARATOR)?.takeIf { it.size == 2 }?.let { fields ->
                StoredFileInfo(
                    name = name,
                    size = fields[0].toDoubleOrNull()?.toLong() ?: 0L,
                    lastModified = fields[1].toDoubleOrNull()?.toLong() ?: 0L,
                )
            }
        }
    }

    override suspend fun exists(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        fileHandle(directory, name) != null
    }

    override suspend fun readText(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        failingAsStorage(name) {
            fileHandle(directory, name)?.let { readFileBytes(it).await()?.toByteArray()?.decodeLibraryText() }
        }
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        failingAsStorage(name) {
            fileHandle(directory, name)?.let { readFileBytes(it).await()?.toByteArray() }
        }
    }

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = withContext(Dispatchers.Default) {
        failingAsStorage(name) { writeFile(directoryHandle(directory), directory.pathSegments.joinToString("/"), name, text.toJsString()).await() }
        Unit
    }

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) = withContext(Dispatchers.Default) {
        failingAsStorage(name) { writeFile(directoryHandle(directory), directory.pathSegments.joinToString("/"), name, bytes.toInt8Array()).await() }
        Unit
    }

    override suspend fun delete(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        failingAsStorage(name) {
            requireValidFileName(name)
            removeEntry(directoryHandle(directory), name).await()
        }
        Unit
    }

    /**
     * A rejected promise surfaces from `await` as a plain `Exception` whose message names the JavaScript error (the
     * coroutines library can only unwrap a Kotlin one), and a synchronous `js(...)` call throws a `JsException`, which
     * is not an `Exception` at all; neither says anything a caller could tell apart from any other failure. Only a file
     * that is not there is folded into null (see `getFileHandle`); everything else the browser refuses - a
     * `NotAllowedError`, a `QuotaExceededError`, a file locked by a writable - is a file or a directory that exists and
     * cannot be used.
     */
    private suspend fun <T> failingAsStorage(name: String, operation: suspend () -> T): T = try {
        operation()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: IllegalArgumentException) {
        // requireValidFileName: a name the caller should never have asked for, which the contract reports as it is.
        throw exception
    } catch (exception: LibraryStorageException) {
        throw exception
    } catch (exception: Throwable) {
        throw LibraryStorageException("Could not access \"$name\".", exception)
    }

    private val StorageDirectory.displayName get() = pathSegments.joinToString("/")

    private suspend fun fileHandle(directory: StorageDirectory, name: String): JsAny? {
        requireValidFileName(name)
        return getFileHandle(directoryHandle(directory), name).await()
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

/**
 * Resolves to `null` instead of rejecting with a `NotFoundError` when the file is not there. Every other rejection is
 * passed on: a file that is there but cannot be reached is not the same as a missing one.
 */
private fun getFileHandle(parent: JsAny, name: String): Promise<JsAny?> = js(
    """parent.getFileHandle(name).catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)

/**
 * Every file of the directory as name, size and last modification time, separated by control characters.
 *
 * An entry that is gone by the time it is asked for its file is left out rather than failing the listing: a sync run
 * deletes files while the live rescan is listing the same directory, and on one thread the two interleave at every
 * `await`. A `NotFoundError` there says the file is not in the directory any more, which is what leaving it out of
 * the listing says too. Every other rejection still fails the listing, since a file that is there and cannot be
 * reached must never be reported as absent - sync plans a deletion for a file it cannot see.
 */
private fun listEntries(directory: JsAny): Promise<JsString?> = js(
    """(async function () {
        var field = String.fromCharCode(0);
        var entries = [];
        for await (var entry of directory.entries()) {
            if (entry[1].kind === 'file') {
                var file;
                try { file = await entry[1].getFile(); }
                catch (error) { if (error && error.name === 'NotFoundError') continue; throw error; }
                entries.push(entry[0] + field + file.size + field + file.lastModified);
            }
        }
        return entries.join(String.fromCharCode(1));
    })()"""
)

private fun listEntryNames(directory: JsAny): Promise<JsString?> = js(
    """(async function () {
        var names = [];
        for await (var name of directory.keys()) names.push(name);
        return names.join(String.fromCharCode(1));
    })()"""
)

/**
 * The size and the last modification time of one file, separated by the same control character `listEntries` uses.
 * Resolves to `null` for a file deleted between the handle and the question, which `info`'s contract calls missing.
 */
private fun fileInfo(handle: JsAny): Promise<JsString?> = js(
    """handle.getFile().then(function (file) {
        return file.size + String.fromCharCode(0) + file.lastModified;
    }).catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)

/**
 * The file's bytes, or `null` for a file removed between its handle and the read: a `NotFoundError` there is a file
 * no longer in the directory, which is what `readFileBytes`' callers call missing, the same way `fileInfo` does.
 */
private fun readFileBytes(handle: JsAny): Promise<Int8Array?> = js(
    """handle.getFile().then(function (file) { return file.arrayBuffer(); }).then(function (buffer) {
        return new Int8Array(buffer);
    }).catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)

/**
 * A writable holds a lock on its file until it is closed or aborted, so one whose write fails is aborted before the
 * failure is passed on: left open, it would make every later write and the deletion of that file fail as well.
 *
 * Where there is no `createWritable()` (Safari before 26), the write is handed to `opfs-writer.js`, a dedicated worker,
 * since `createSyncAccessHandle()` exists nowhere else; it is given the directory by its path and the content as bytes.
 */
private fun writeFile(parent: JsAny, path: String, name: String, data: JsAny): Promise<JsAny?> = js(
    """(async function () {
        var existed = true;
        var handle;
        try { handle = await parent.getFileHandle(name); }
        catch (error) { if (!error || error.name !== 'NotFoundError') throw error; existed = false; handle = await parent.getFileHandle(name, { create: true }); }
        try {
            if (typeof handle.createWritable === 'function') {
                var writable = await handle.createWritable();
                try { await writable.write(data); await writable.close(); }
                catch (error) { try { await writable.abort(); } catch (ignored) { } throw error; }
            } else {
                var worker = new Worker('opfs-writer.js');
                await new Promise(function (resolve, reject) {
                    worker.onmessage = function (event) { worker.terminate(); event.data.error ? reject(Object.assign(new Error(event.data.message), { name: event.data.error })) : resolve(); };
                    worker.onerror = function (event) { worker.terminate(); reject(event.error || new Error(event.message)); };
                    var bytes = typeof data === 'string' ? new TextEncoder().encode(data) : data;
                    worker.postMessage({ id: 1, path: path.split('/'), name: name, data: bytes });
                });
            }
        } catch (error) { if (!existed) try { await parent.removeEntry(name); } catch (ignored) { } throw error; }
        return null;
    })()"""
)

/**
 * Resolves instead of rejecting with a `NotFoundError` when the file is not there, which makes deleting a missing file
 * a no-op. Every other rejection is passed on, since a rename writes the new file before it deletes the old one and
 * a deletion reported as done when it was refused would leave the song there twice.
 */
private fun removeEntry(parent: JsAny, name: String): Promise<JsAny?> = js(
    """parent.removeEntry(name).catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)

private const val FIELD_SEPARATOR_CODE = 0
private const val ENTRY_SEPARATOR_CODE = 1
