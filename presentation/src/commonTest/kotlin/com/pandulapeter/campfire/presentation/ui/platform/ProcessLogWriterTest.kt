/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessLogWriterTest {

    @Test
    fun `every line is stamped once, however it is written`() {
        val store = MemoryStore()
        val writer = writer(store)

        writer.append("Hel".encodeToByteArray())
        writer.append("lo\nWorld\nAnd".encodeToByteArray())
        writer.append(" more\n".encodeToByteArray())

        assertEquals("[T] Hello\n[T] World\n[T] And more\n", writer.text())
    }

    @Test
    fun `a full file is rotated at the start of a line, keeping the one before`() {
        val store = MemoryStore()
        val writer = writer(store, maxBytes = 20)

        writer.append("[first line]\n".encodeToByteArray())
        writer.append("[second line]\n".encodeToByteArray())
        writer.append("[third line]\n".encodeToByteArray())

        assertEquals("[T] [second line]\n", store.previous.decodeToString())
        assertEquals("[T] [third line]\n", store.current.decodeToString())
    }

    @Test
    fun `a file left full by the last session is rotated before anything is written`() {
        val store = MemoryStore(current = ByteArray(30) { 'x'.code.toByte() })
        val writer = writer(store, maxBytes = 20)

        writer.append("new\n".encodeToByteArray())

        assertEquals(30, store.previous.size)
        assertEquals("[T] new\n", store.current.decodeToString())
    }

    @Test
    fun `a store that fails is given up on and said so once`() {
        val failures = mutableListOf<String>()
        val store = MemoryStore(isFailing = true)
        val writer = writer(store, onFailure = { failures += it })

        writer.append("one\n".encodeToByteArray())
        writer.append("two\n".encodeToByteArray())

        assertEquals(1, failures.size)
        assertTrue(store.appendAttempts == 1)
    }

    private fun writer(store: MemoryStore, maxBytes: Long = 1024, onFailure: (String) -> Unit = {}) =
        ProcessLogWriter(store = store, maxBytes = maxBytes, onFailure = onFailure, now = { "T" })

    private fun ProcessLogWriter.text() = read()?.decodeToString()

    private class MemoryStore(
        var current: ByteArray = ByteArray(0),
        private val isFailing: Boolean = false,
    ) : ProcessLogStore {
        var previous = ByteArray(0)
        var appendAttempts = 0

        override val currentSize get() = current.size.toLong()

        override fun append(bytes: ByteArray, offset: Int, length: Int) {
            appendAttempts++
            if (isFailing) error("The disk is full.")
            current += bytes.copyOfRange(offset, offset + length)
        }

        override fun rotate() {
            previous = current
            current = ByteArray(0)
        }

        override fun read() = previous + current
    }
}
