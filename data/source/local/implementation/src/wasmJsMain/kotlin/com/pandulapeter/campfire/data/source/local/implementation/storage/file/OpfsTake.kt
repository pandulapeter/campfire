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

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.await
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

/**
 * Copies [file], a `File` the user picked or dropped, to [name] in [parent] ([path] from the root, for the worker) without
 * a byte of it crossing into Wasm: a range of [TAKE_RANGE_SIZE] at a time, each one awaited, so that [onProgress] hears
 * how far it is and a cancellation stops it between ranges, never by a timer a hidden tab would throttle. A
 * `createWritable()` stream is handed each range as a `Blob` slice, which the browser reads from disk itself; without one
 * (Safari before 26) the worker's `stage` request copies the range through its sync access handle. Whatever ends the copy
 * but its last range (a quota refused, which lands on a write, a cancellation) removes the partial file before it
 * reaches the caller.
 */
internal suspend fun takeFile(parent: JsAny, path: String, name: String, file: JsAny, onProgress: (Long) -> Unit) {
    val copy = openStagedCopy(parent, path, name, file).await<JsAny>()
    var isWhole = false
    try {
        while (true) {
            currentCoroutineContext().ensureActive()
            onProgress(copyStagedRange(copy, TAKE_RANGE_SIZE.toDouble()).await<JsNumber>().toDouble().toLong())
            if (isStagedCopyWhole(copy)) break
        }
        finishStagedCopy(copy).await<JsAny?>()
        isWhole = true
    } finally {
        if (!isWhole) {
            withContext(NonCancellable) {
                try {
                    abortStagedCopy(copy).await<JsAny?>()
                } catch (exception: Throwable) {
                    println("Could not remove \"$name\": ${exception.message}")
                }
            }
        }
    }
}

private fun openStagedCopy(parent: JsAny, path: String, name: String, file: JsAny): Promise<JsAny> = js(
    """(async function () {
        if (typeof FileSystemFileHandle === 'undefined' || typeof FileSystemFileHandle.prototype.createWritable !== 'function') {
            return { request: { path: path.split('/'), name: name }, parent: parent, name: name, file: file, position: 0 };
        }
        var handle = await parent.getFileHandle(name, { create: true });
        var writable = await handle.createWritable({ keepExistingData: false });
        return { writable: writable, parent: parent, name: name, file: file, position: 0 };
    })()"""
)

private fun copyStagedRange(copy: JsAny, length: Double): Promise<JsNumber> = js(
    """(async function () {
        var end = Math.min(copy.position + length, copy.file.size);
        if (copy.writable) await copy.writable.write(copy.file.slice(copy.position, end));
        else await window.__campfireSendToOpfsWriter(Object.assign({ stage: copy.file, from: copy.position, to: end }, copy.request));
        copy.position = end;
        return end;
    })()"""
)

private fun isStagedCopyWhole(copy: JsAny): Boolean = js("copy.position >= copy.file.size")

private fun finishStagedCopy(copy: JsAny): Promise<JsAny?> = js("copy.writable ? copy.writable.close() : Promise.resolve(null)")

private fun abortStagedCopy(copy: JsAny): Promise<JsAny?> = js(
    """(async function () {
        if (copy.writable) try { await copy.writable.abort(); } catch (ignored) { }
        try { await copy.parent.removeEntry(copy.name); } catch (error) { if (!error || error.name !== 'NotFoundError') throw error; }
        return null;
    })()"""
)

/** How much of a picked file one awaited step copies: a step a second or so on a phone, few enough for the overhead. */
private const val TAKE_RANGE_SIZE = 8 * 1024 * 1024
