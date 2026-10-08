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

    /** Names only, which opens no file: [readScan] asks each file for its size and date as it reads it. */
    override suspend fun listForScan(directory: StorageDirectory) = listNames(directory).sorted().map { ScanEntry(name = it, info = null) }

    /**
     * [readTexts] with each file's size and date, which the browser hands over with the same `getFile()` the read needs
     * anyway, so a scan asks every song for its file once rather than once to list it and once again to read it. Its
     * listing names directories too, which are answered as missing, as [list] leaves them out.
     */
    override suspend fun readScan(directory: StorageDirectory, entries: List<ScanEntry>, maxSize: Long): List<ScannedFile> = withContext(Dispatchers.Default) {
        val validNames = entries.map { it.name }.filter(::isValidFileName)
        val answers = try {
            if (validNames.isEmpty()) {
                emptyList()
            } else {
                failingAsStorage(directory.displayName) {
                    readFileScans(directoryHandle(directory), validNames.joinToString(ENTRY_SEPARATOR), maxSize.toDouble()).await<JsArray<JsString?>>()
                }.let { array -> List(array.length) { array[it]?.toString() } }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            return@withContext entries.map { ScannedFile.Failed(exception) }
        }
        val remainingAnswers = answers.iterator()
        entries.map { entry ->
            val name = entry.name
            if (!isValidFileName(name)) {
                ScannedFile.Failed(IllegalArgumentException("Invalid file name: \"$name\"."))
            } else {
                val answer = remainingAnswers.next()
                when {
                    answer == null -> ScannedFile.Missing
                    answer.startsWith(READ_FAILED) -> ScannedFile.Failed(
                        LibraryStorageException("Could not access \"$name\".", Exception(answer.substring(1))),
                    )
                    else -> {
                        // The size and the date come first, each followed by a space, and the text, where there is one, after them.
                        val sizeEnd = answer.indexOf(' ', startIndex = 1)
                        val dateEnd = answer.indexOf(' ', startIndex = sizeEnd + 1).let { if (it < 0) answer.length else it }
                        val info = StoredFileInfo(
                            name = name,
                            size = answer.substring(1, sizeEnd).toDoubleOrNull()?.toLong() ?: 0L,
                            lastModified = answer.substring(sizeEnd + 1, dateEnd).toDoubleOrNull()?.toLong() ?: 0L,
                        )
                        when (answer.first()) {
                            DECODED_TEXT -> ScannedFile.Text(info = info, text = answer.substring(dateEnd + 1).trimStart(BYTE_ORDER_MARK))
                            TOO_LARGE -> ScannedFile.TooLarge(info)
                            else -> try {
                                readBytes(directory, name)?.decodeLibraryText()?.let { ScannedFile.Text(info = info, text = it) } ?: ScannedFile.Missing
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: Exception) {
                                ScannedFile.Failed(exception)
                            }
                        }
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
        const val TOO_LARGE = '\u0003'

        // Control characters, because they are the only thing a file name is guaranteed not to contain.
        val ENTRY_SEPARATOR = Char(ENTRY_SEPARATOR_CODE).toString()
        val FIELD_SEPARATOR = Char(FIELD_SEPARATOR_CODE).toString()
    }
}
