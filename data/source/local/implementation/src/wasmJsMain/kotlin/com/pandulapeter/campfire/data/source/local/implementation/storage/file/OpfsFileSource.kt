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

import com.pandulapeter.campfire.data.model.web.copyInto
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.withContext
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

/**
 * The web's [FileSource]: slices of one `File` snapshot, each read with `arrayBuffer()` and carried across the boundary
 * a chunk at a time ([copyInto]). A position crosses as a JavaScript number, exact up to 2^53 bytes.
 */
internal class OpfsFileSource(
    private val file: JsAny,
    private val name: String,
) : FileSource {

    override val size = fileSize(file).toLong()

    override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int = withContext(Dispatchers.Default) {
        if (position >= size || length == 0) return@withContext 0
        val end = minOf(size, position + length)
        val slice = try {
            readFileSlice(file, position.toDouble(), end.toDouble()).await<JsAny>()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            throw LibraryStorageException("Could not read \"$name\".", exception)
        }
        slice.copyInto(buffer, offset)
    }

    override suspend fun close() = Unit
}

/** The file of [handle] as it is now, `null` for one deleted since the handle was taken. */
internal fun openFileSnapshot(handle: JsAny): Promise<JsAny?> = js(
    """handle.getFile().catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)

private fun fileSize(file: JsAny): Double = js("file.size")

private fun readFileSlice(file: JsAny, start: Double, end: Double): Promise<JsAny> =
    js("file.slice(start, end).arrayBuffer().then(function (buffer) { return new Uint8Array(buffer); })")

/** What the origin may still store, its quota less its usage, as `navigator.storage.estimate()` guesses it. */
internal fun storageSpaceLeft(): Promise<JsNumber> =
    js("navigator.storage.estimate().then(function (estimate) { return Math.max(0, (estimate.quota || 0) - (estimate.usage || 0)); })")
