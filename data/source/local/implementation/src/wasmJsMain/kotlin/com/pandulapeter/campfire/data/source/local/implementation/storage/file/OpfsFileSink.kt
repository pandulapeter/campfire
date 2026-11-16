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

import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.await
import kotlinx.coroutines.withContext
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

/**
 * The web's [FileSink]: a writable stream where the browser has `createWritable()`, whose bytes reach the file only on
 * [close], else the `opfs-writer.js` worker's sync access handle (see [openFileStream]). The bytes cross into the
 * browser one write at a time, never the whole export.
 */
internal class OpfsFileSink(
    private val stream: JsAny,
    private val name: String,
) : FileSink {

    override var position = 0L
        private set

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = withContext(Dispatchers.Default) {
        if (length == 0) return@withContext
        failingAsStorage { writeFileStream(stream, latin1ToBytes(bytes.copyOfRange(offset, offset + length).toLatin1JsString())).await() }
        position += length
    }

    override suspend fun close() = withContext(Dispatchers.Default) { failingAsStorage { closeFileStream(stream).await() } }

    override suspend fun abandon() = withContext(NonCancellable + Dispatchers.Default) {
        try {
            abortFileStream(stream).await<JsAny?>()
        } catch (exception: Throwable) {
            println("Could not remove \"$name\": ${exception.message}")
        }
        Unit
    }

    private suspend fun failingAsStorage(operation: suspend () -> Unit) = try {
        operation()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Throwable) {
        throw LibraryStorageException("Could not write \"$name\".", exception)
    }
}

/**
 * Creates (or empties) [name] in [parent], the directory at [path], and answers the stream [writeFileStream] and the
 * rest take: an object holding either the writable or the worker's address of the file.
 */
internal fun openFileStream(parent: JsAny, path: String, name: String): Promise<JsAny?> = js(
    """(async function () {
        if (typeof FileSystemFileHandle === 'undefined' || typeof FileSystemFileHandle.prototype.createWritable !== 'function') {
            var request = { path: path.split('/'), name: name };
            await window.__campfireSendToOpfsWriter(Object.assign({ stream: 'open' }, request));
            return { request: request };
        }
        var handle = await parent.getFileHandle(name, { create: true });
        return { parent: parent, name: name, writable: await handle.createWritable({ keepExistingData: false }) };
    })()"""
)

internal fun writeFileStream(stream: JsAny, data: JsAny): Promise<JsAny?> = js(
    """stream.writable ? stream.writable.write(data) : window.__campfireSendToOpfsWriter(Object.assign({ stream: 'append', data: data }, stream.request))"""
)

internal fun closeFileStream(stream: JsAny): Promise<JsAny?> = js(
    """stream.writable ? stream.writable.close() : window.__campfireSendToOpfsWriter(Object.assign({ stream: 'close' }, stream.request))"""
)

/** A writable's bytes are only in its swap file, which aborting discards, so the empty file it created is removed too. */
internal fun abortFileStream(stream: JsAny): Promise<JsAny?> = js(
    """(async function () {
        if (!stream.writable) return window.__campfireSendToOpfsWriter(Object.assign({ stream: 'abort' }, stream.request));
        try { await stream.writable.abort(); } catch (ignored) { }
        try { await stream.parent.removeEntry(stream.name); } catch (error) { if (!error || error.name !== 'NotFoundError') throw error; }
        return null;
    })()"""
)
