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

    override suspend fun readText(directory: StorageDirectory, name: String) = when (val answer = readTexts(directory, listOf(name)).single()) {
        is BatchRead.Text -> answer.text
        BatchRead.Missing -> null
        is BatchRead.Failed -> throw answer.cause
    }

    /**
     * The whole batch is one `js(...)` call and one promise, rather than a handle, a file and a buffer awaited in turn for
     * every name. Valid UTF-8 without a NUL is decoded by the browser, which gives exactly what `decodeLibraryText`
     * would, a single byte order mark aside, which `TextDecoder` strips and the rest of the leading ones are stripped
     * here. Anything else, the code-page files and UTF-16, is read again as bytes and goes through `decodeLibraryText`,
     * since only it knows those rules - a NUL anywhere is a file that may be UTF-16 without a byte order mark, which the
     * browser would have read as valid UTF-8.
     */
    override suspend fun readTexts(directory: StorageDirectory, names: List<String>): List<BatchRead> = withContext(Dispatchers.Default) {
        val validNames = names.filter(::isValidFileName)
        val answers = try {
            if (validNames.isEmpty()) {
                emptyList()
            } else {
                failingAsStorage(directory.displayName) {
                    readFileTexts(directoryHandle(directory), validNames.joinToString(ENTRY_SEPARATOR)).await<JsArray<JsString?>>()
                }.let { array -> List(array.length) { array[it]?.toString() } }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            return@withContext names.map { BatchRead.Failed(exception) }
        }
        val remainingAnswers = answers.iterator()
        names.map { name ->
            if (!isValidFileName(name)) {
                BatchRead.Failed(IllegalArgumentException("Invalid file name: \"$name\"."))
            } else {
                val answer = remainingAnswers.next()
                when {
                    answer == null -> BatchRead.Missing
                    answer.startsWith(DECODED_TEXT) -> BatchRead.Text(answer.substring(1).trimStart(BYTE_ORDER_MARK))
                    answer.startsWith(READ_FAILED) -> BatchRead.Failed(
                        LibraryStorageException("Could not access \"$name\".", Exception(answer.substring(1))),
                    )
                    else -> try {
                        readBytes(directory, name)?.decodeLibraryText()?.let(BatchRead::Text) ?: BatchRead.Missing
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        BatchRead.Failed(exception)
                    }
                }
            }
        }
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.Default) {
        failingAsStorage(name) {
            fileHandle(directory, name)?.let { handle -> readFileLatin1(handle).await<JsString?>()?.toString()?.latin1Bytes() }
        }
    }

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = withContext(Dispatchers.Default) {
        write(directory, name) { handle -> writeFile(handle, directory.displayName, name, text.toJsString()) }
    }

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) = withContext(Dispatchers.Default) {
        write(directory, name) { handle -> writeFile(handle, directory.displayName, name, latin1ToBytes(bytes.toLatin1JsString())) }
    }

    /**
     * A write the worker could neither finish nor undo leaves its journal behind (see `opfs-writer.js`), and the file
     * damaged until that journal is played back. Forgetting the directory's handle is what makes the next access to
     * the directory play it back, rather than the next start of the app.
     */
    private suspend fun write(directory: StorageDirectory, name: String, write: (directoryHandle: JsAny) -> Promise<JsAny?>) {
        try {
            failingAsStorage(name) { write(directoryHandle(directory)).await() }
        } catch (exception: LibraryStorageException) {
            directoryHandlesMutex.withLock { directoryHandles.remove(directory) }
            throw exception
        }
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
     * The directory handles, resolved once. Walking down from the root is three promises, and doing that for
     * every one of the reads of a library scan (which already run in parallel) tripled the number of calls into
     * OPFS. Nothing outside the page can remove a directory from the origin private file system, so a handle that
     * was resolved once stays valid.
     *
     * A handle is only handed out once the journal of an interrupted worker write in its directory has been played
     * back (see `opfs-writer.js`), so nothing is ever read from a file the worker left half written. That happens under
     * the same lock, once per directory: a directory with nothing to recover costs one walk over its names.
     */
    private val directoryHandles = mutableMapOf<StorageDirectory, JsAny>()
    private val directoryHandlesMutex = Mutex()

    private suspend fun directoryHandle(directory: StorageDirectory): JsAny = directoryHandles[directory] ?: directoryHandlesMutex.withLock {
        directoryHandles.getOrPut(directory) {
            if (!isOpfsAvailable()) {
                throw IllegalStateException(OPFS_UNAVAILABLE)
            }
            installOpfsWriter()
            var handle = opfsRoot().await() ?: throw IllegalStateException(OPFS_UNAVAILABLE)
            directory.pathSegments.forEach { segment ->
                handle = getDirectoryHandle(handle, segment).await() ?: throw IllegalStateException(OPFS_UNAVAILABLE)
            }
            failingAsStorage(directory.displayName) { recoverInterruptedWrites(handle, directory.displayName).await() }
            handle
        }
    }

    private companion object {

        const val OPFS_UNAVAILABLE = "OPFS unavailable"
        const val FIELD_COUNT = 3
        const val BYTE_ORDER_MARK = '\uFEFF'
        const val DECODED_TEXT = '\u0000'
        const val READ_FAILED = '\u0002'

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
        var handles = [];
        for await (var entry of directory.entries()) if (entry[1].kind === 'file' && !entry[0].endsWith('.campfire-tmp') && !entry[0].endsWith('.campfire-commit') && !entry[0].endsWith('.campfire-new')) handles.push(entry);
        var files = await Promise.all(handles.map(function (entry) {
            return entry[1].getFile().catch(function (error) { if (error && error.name === 'NotFoundError') return null; throw error; });
        }));
        var entries = [];
        for (var i = 0; i < handles.length; i++) if (files[i]) entries.push(handles[i][0] + field + files[i].size + field + files[i].lastModified);
        return entries.join(String.fromCharCode(1));
    })()"""
)

private fun listEntryNames(directory: JsAny): Promise<JsString?> = js(
    """(async function () {
        var names = [];
        for await (var name of directory.keys()) if (!name.endsWith('.campfire-tmp') && !name.endsWith('.campfire-commit') && !name.endsWith('.campfire-new')) names.push(name);
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
 * The text of every file of [names] (separated by the same control character `listEntries` uses), all of them asked
 * for at once, one answer each and in order: the text as `TextDecoder` reads it behind a leading NUL, a lone SOH where
 * the file has to be decoded by `decodeLibraryText` instead (not valid UTF-8, or a NUL in it), `null` for a file that
 * is not there, and STX with the error's name and message for one that is there and could not be read. A failure is its
 * own answer rather than the batch's, and the texts cross into Kotlin as strings, where bytes would cross one call each.
 */
private fun readFileTexts(directory: JsAny, names: String): Promise<JsArray<JsString?>> = js(
    """Promise.all((names.length === 0 ? [] : names.split(String.fromCharCode(1))).map(function (name) {
        return directory.getFileHandle(name).then(function (handle) { return handle.getFile(); }).then(function (file) {
            return file.arrayBuffer();
        }).then(function (buffer) {
            var text;
            try { text = new TextDecoder('utf-8', { fatal: true }).decode(buffer); }
            catch (error) { return String.fromCharCode(1); }
            return text.indexOf(String.fromCharCode(0)) >= 0 ? String.fromCharCode(1) : String.fromCharCode(0) + text;
        }).catch(function (error) {
            if (error && error.name === 'NotFoundError') return null;
            return String.fromCharCode(2) + ((error && error.name) || 'Error') + ': ' + ((error && error.message) || String(error));
        });
    }))"""
)

/**
 * The file's bytes as a string of one character per byte, or `null` for a file that is not there. A string is the
 * one thing that crosses the Kotlin/Wasm boundary in bulk; an `Int8Array` is read one call per element. It is built a
 * chunk at a time, since `fromCharCode` takes its bytes as arguments and an argument list has a limit.
 */
private fun readFileLatin1(handle: JsAny): Promise<JsString?> = js(
    """handle.getFile().then(function (file) { return file.arrayBuffer(); }).then(function (buffer) {
        var bytes = new Uint8Array(buffer);
        var parts = [];
        for (var i = 0; i < bytes.length; i += 0x8000) parts.push(String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000)));
        return parts.join('');
    }).catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)

/** The bytes `toLatin1JsString` carried across, for `writeFile`, which writes a string as UTF-8. */
private fun latin1ToBytes(text: JsString): JsAny = js(
    """(function () {
        var bytes = new Uint8Array(text.length);
        for (var i = 0; i < text.length; i++) bytes[i] = text.charCodeAt(i);
        return bytes;
    })()"""
)

private fun String.latin1Bytes() = ByteArray(length) { this[it].code.toByte() }

private fun ByteArray.toLatin1JsString() = CharArray(size) { (this[it].toInt() and 0xFF).toChar() }.concatToString().toJsString()

/**
 * The page's one `opfs-writer.js` worker, started by the first request that needs it, with its replies matched to the
 * requests by their id: starting one per write fetched, parsed and started the script again for every song of an
 * import. The worker queues the requests itself. One that fails as a whole fails every request waiting on it and is
 * dropped, so that the next request starts a new one; being a global of the page, it never outlives the version that
 * started it. Its address carries a version of the script's own content (see `index.html` in `:app:web`), because a
 * worker the browser still had from another release would be handed requests in a shape it may not understand.
 *
 * A global function rather than a `js(...)` block of its own, since both the writes and the recovery send requests
 * and a `js(...)` block cannot call another.
 */
private fun installOpfsWriter(): Unit = js(
    """{
        if (window.__campfireSendToOpfsWriter) return;
        window.__campfireSendToOpfsWriter = function (request) {
            var writer = window.__campfireOpfsWriter;
            if (!writer) {
                writer = window.__campfireOpfsWriter = { worker: new Worker(window.campfireVersioned('opfs-writer.js')), next: 1, pending: new Map() };
                writer.worker.onmessage = function (event) {
                    var entry = writer.pending.get(event.data.id);
                    if (!entry) return;
                    writer.pending.delete(event.data.id);
                    event.data.error ? entry.reject(Object.assign(new Error(event.data.message), { name: event.data.error })) : entry.resolve();
                };
                writer.worker.onerror = function (event) {
                    var error = event.error || new Error(event.message);
                    writer.pending.forEach(function (entry) { entry.reject(error); });
                    writer.pending.clear();
                    writer.worker.terminate();
                    if (window.__campfireOpfsWriter === writer) window.__campfireOpfsWriter = null;
                };
            }
            return new Promise(function (resolve, reject) {
                request.id = writer.next++;
                writer.pending.set(request.id, { resolve: resolve, reject: reject });
                try { writer.worker.postMessage(request); }
                catch (error) { writer.pending.delete(request.id); reject(error); }
            });
        };
    }"""
)

/**
 * Plays back what every write in this directory left unfinished. A `<name>.campfire-new` marker is the page's own (see
 * [writeFile]): a new file whose first write never closed, which is removed while it is still empty, since an empty file
 * under a song's name would be read as a song, and uploaded by sync as one. The other journal files are the worker's
 * (see `opfs-writer.js`), and only ever exist where the worker writes, so a browser with `createWritable()` never finds
 * any and never starts the worker for this.
 */
private fun recoverInterruptedWrites(directory: JsAny, path: String): Promise<JsAny?> = js(
    """(async function () {
        var created = [];
        var hasWorkerJournal = false;
        for await (var name of directory.keys()) {
            if (name.endsWith('.campfire-new')) created.push(name.slice(0, -'.campfire-new'.length));
            else if (name.endsWith('.campfire-tmp') || name.endsWith('.campfire-commit')) hasWorkerJournal = true;
        }
        var ignoreMissing = function (error) { if (!error || error.name !== 'NotFoundError') throw error; };
        for (var i = 0; i < created.length; i++) {
            var file = null;
            try { file = await (await directory.getFileHandle(created[i])).getFile(); } catch (error) { ignoreMissing(error); }
            if (file && file.size === 0) try { await directory.removeEntry(created[i]); } catch (error) { ignoreMissing(error); }
            try { await directory.removeEntry(created[i] + '.campfire-new'); } catch (error) { ignoreMissing(error); }
        }
        if (hasWorkerJournal) await window.__campfireSendToOpfsWriter({ path: path.split('/'), recover: true });
        return null;
    })()"""
)

/**
 * A writable holds a lock on its file until it is closed or aborted, so one whose write fails is aborted before the
 * failure is passed on: left open, it would make every later write and the deletion of that file fail as well.
 *
 * Creating a file puts it under its final name, empty, before anything is written, and a page that is closed before the
 * write ends cannot take it back. So a new file is announced first by an empty `<name>.campfire-new`, which
 * [recoverInterruptedWrites] uses to remove an empty leftover, and which is removed once the write is closed - except
 * for a name within its 12 bytes of the 255 bytes Safari's file system allows, where the marker could never be created
 * and the file is written without one, as an existing file always is (the writable's swap file keeps that one whole).
 * Decided by the length rather than by a failure to create the marker, so that a real failure of the storage is never
 * taken for that.
 *
 * Where there is no `createWritable()` (Safari before 26), the write is handed to `opfs-writer.js`, a dedicated worker
 * (see [installOpfsWriter]), since `createSyncAccessHandle()` exists nowhere else; it is given the directory by its
 * path and the content as bytes. The page creates nothing there: the worker creates the file only after its own
 * journal says what it is to hold.
 */
private fun writeFile(parent: JsAny, path: String, name: String, data: JsAny): Promise<JsAny?> = js(
    """(async function () {
        if (typeof FileSystemFileHandle === 'undefined' || typeof FileSystemFileHandle.prototype.createWritable !== 'function') {
            var bytes = typeof data === 'string' ? new TextEncoder().encode(data) : data;
            await window.__campfireSendToOpfsWriter({ path: path.split('/'), name: name, data: bytes });
            return null;
        }
        var existed = true;
        var marker = null;
        var handle;
        try { handle = await parent.getFileHandle(name); }
        catch (error) {
            if (!error || error.name !== 'NotFoundError') throw error;
            existed = false;
            if (new TextEncoder().encode(name).length <= 243) {
                marker = name + '.campfire-new';
                await parent.getFileHandle(marker, { create: true });
            }
            try { handle = await parent.getFileHandle(name, { create: true }); }
            catch (creationError) { if (marker) try { await parent.removeEntry(marker); } catch (ignored) { } throw creationError; }
        }
        try {
            var writable = await handle.createWritable();
            try { await writable.write(data); await writable.close(); }
            catch (error) { try { await writable.abort(); } catch (ignored) { } throw error; }
        } catch (error) {
            if (!existed) try { await parent.removeEntry(name); } catch (ignored) { }
            if (marker) try { await parent.removeEntry(marker); } catch (ignored) { }
            throw error;
        }
        // The file is whole by now, so a marker that cannot be removed only leaves recovery a file it keeps.
        if (marker) try { await parent.removeEntry(marker); } catch (ignored) { }
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
