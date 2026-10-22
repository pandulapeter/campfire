/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalTime::class)

package com.pandulapeter.campfire.presentation.ui.platform

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The two files a process log is kept in: the one being written and the one before it. Calls never overlap, since every
 * platform that prints from more than one thread holds a lock around the writer. Any call may throw.
 */
internal interface ProcessLogStore {

    /** The size of the file being written, read once, as the writer starts. */
    val currentSize: Long

    fun append(bytes: ByteArray, offset: Int, length: Int)

    /** Makes the file being written the previous one, replacing that, and starts an empty one. */
    fun rotate()

    /** The previous file followed by the current one. */
    fun read(): ByteArray
}

/**
 * Stamps every line of what the process prints with the time it was written at, which is what lines a log up with what
 * the user remembers, and keeps the log bounded: once the file being written reaches [maxBytes], it becomes the previous
 * one and a new one is started, so a session that runs for weeks holds at most twice that. Files are only ever rotated at
 * the start of a line, so that no line is split between the two.
 *
 * Nothing here may ever throw at the app, which prints from everywhere: a store that fails is given up on for the rest of
 * the session, and said so once through [onFailure], rather than tried again for every line the app writes. [onFailure]
 * must not print where the process's output is what is being logged.
 */
internal class ProcessLogWriter(
    private val store: ProcessLogStore,
    private val maxBytes: Long,
    private val onFailure: (String) -> Unit,
    private val now: () -> String = { Clock.System.now().toString() },
) {
    private var size = 0L
    private var isAtLineStart = true
    private var isBroken = false

    init {
        guard {
            size = store.currentSize
            if (size >= maxBytes) rotate()
        }
    }

    fun append(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) = guard {
        val end = offset + length
        var start = offset
        while (start < end) {
            if (isAtLineStart) {
                val prefix = "[${now()}] ".encodeToByteArray()
                if (size + prefix.size >= maxBytes) rotate()
                write(prefix, 0, prefix.size)
                isAtLineStart = false
            }
            val newLine = bytes.indexOfNewLine(start, end)
            val lineEnd = if (newLine == -1) end else newLine + 1
            write(bytes, start, lineEnd - start)
            isAtLineStart = newLine != -1
            start = lineEnd
        }
    }

    /** What the store holds, even after a write failed, since what came before it can still be read. */
    fun read(): ByteArray? = try {
        store.read()
    } catch (exception: Exception) {
        onFailure("Could not read the log file: ${exception.message}")
        null
    }

    private fun write(bytes: ByteArray, offset: Int, length: Int) {
        store.append(bytes, offset, length)
        size += length
    }

    private fun rotate() {
        store.rotate()
        size = 0
    }

    private inline fun guard(block: () -> Unit) {
        if (isBroken) return
        try {
            block()
        } catch (exception: Exception) {
            isBroken = true
            onFailure("Could not write the log file: ${exception.message}")
        }
    }

    private fun ByteArray.indexOfNewLine(start: Int, end: Int): Int {
        for (index in start until end) if (this[index] == NEW_LINE) return index
        return -1
    }

    private companion object {
        const val NEW_LINE = '\n'.code.toByte()
    }
}

/**
 * How large the file being written grows before it is rotated, see [ProcessLogWriter]: about three thousand lines, many
 * sessions and a few stack traces, while a report needs the last one or two. The same everywhere, the web's
 * `localStorage` included, which keeps text as UTF-16 in a few megabytes for the whole site.
 */
internal const val PROCESS_LOG_MAX_BYTES = 256L * 1024
