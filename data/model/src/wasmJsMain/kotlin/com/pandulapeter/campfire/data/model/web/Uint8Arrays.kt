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

package com.pandulapeter.campfire.data.model.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.toJsString

/**
 * A `Uint8Array`'s bytes. They cross the Kotlin/Wasm boundary as strings of one character per byte, the one thing that
 * crosses in bulk (a `Uint8Array` is read one call per element), and [LATIN1_CHUNK_SIZE] at a time rather than as one
 * string of the whole file: that string would hold a 50 MB attachment three times over (the page's one byte a
 * character, then the module's two), on top of the file's bytes on both sides, more than a phone's tab spares. Chunked,
 * a file costs its bytes on each side and a chunk.
 */
fun JsAny.toByteArray(): ByteArray = ByteArray(byteLength(this)).also { copyInto(it, 0) }

/** Copies a `Uint8Array`'s bytes into [target] at [offset], as [toByteArray] carries them, and answers how many. */
fun JsAny.copyInto(target: ByteArray, offset: Int): Int {
    val size = byteLength(this)
    var start = 0
    while (start < size) {
        val end = minOf(start + LATIN1_CHUNK_SIZE, size)
        val chunk = latin1Chunk(this, start, end).toString()
        for (index in chunk.indices) target[offset + start + index] = chunk[index].code.toByte()
        start = end
    }
    return size
}

/**
 * The bytes from [from] to [to] as a new `Uint8Array`, for `writeFile` and the file streams, carried across a chunk at a
 * time for the reason [toByteArray] gives.
 */
fun ByteArray.toUint8Array(from: Int = 0, to: Int = size): JsAny {
    val array = newBytes(to - from)
    var start = from
    while (start < to) {
        val end = minOf(start + LATIN1_CHUNK_SIZE, to)
        val first = start
        val chunk = CharArray(end - first) { (this[first + it].toInt() and 0xFF).toChar() }.concatToString()
        putLatin1Chunk(array, first - from, chunk.toJsString())
        start = end
    }
    return array
}

private fun byteLength(bytes: JsAny): Int = js("bytes.length")

/** One chunk as a string; `fromCharCode` takes its bytes as arguments, and an argument list has a limit. */
private fun latin1Chunk(bytes: JsAny, from: Int, to: Int): JsString = js("String.fromCharCode.apply(null, bytes.subarray(from, to))")

private fun newBytes(size: Int): JsAny = js("new Uint8Array(size)")

private fun putLatin1Chunk(bytes: JsAny, offset: Int, text: JsString): Unit =
    js("{ for (var i = 0; i < text.length; i++) bytes[offset + i] = text.charCodeAt(i); }")

private const val LATIN1_CHUNK_SIZE = 0x8000
