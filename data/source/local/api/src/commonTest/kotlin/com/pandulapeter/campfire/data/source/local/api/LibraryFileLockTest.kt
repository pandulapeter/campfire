/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/** A coroutine taking the lock it already holds would wait for itself forever, so it fails at once instead. */
internal class LibraryFileLockTest {

    private val lock = LibraryFileLock()

    @Test
    fun `taking the lock inside its own block fails instead of hanging`() = runTest(timeout = TIMEOUT) {
        assertFailsWith<IllegalStateException> { lock.withLock { lock.withLock { } } }
    }

    @Test
    fun `a child started inside the block taking the lock fails too`() = runTest(timeout = TIMEOUT) {
        assertFailsWith<IllegalStateException> {
            lock.withLock { coroutineScope { async { lock.withLock { } }.await() } }
        }
    }

    @Test
    fun `sibling coroutines take the lock one after the other`() = runTest(timeout = TIMEOUT) {
        val taken = mutableListOf<Int>()

        coroutineScope { listOf(1, 2).forEach { index -> launch { lock.withLock { taken += index } } } }

        assertEquals(listOf(1, 2), taken)
    }

    @Test
    fun `another lock is taken inside this one, and this one still refuses inside it`() = runTest(timeout = TIMEOUT) {
        val other = LibraryFileLock()

        assertEquals(1, lock.withLock { other.withLock { 1 } })
        assertFailsWith<IllegalStateException> { lock.withLock { other.withLock { lock.withLock { } } } }
    }

    @Test
    fun `the lock is free again after its block throws`() = runTest(timeout = TIMEOUT) {
        assertFailsWith<IllegalArgumentException> { lock.withLock { throw IllegalArgumentException() } }

        assertEquals(1, lock.withLock { 1 })
    }

    private companion object {
        val TIMEOUT = 5.seconds
    }
}
