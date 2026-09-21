/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.model.DataState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a partial publish is allowed to do. None of it can be seen once a load has finished, which is exactly why it
 * is worth pinning down: half a library on screen is better than nothing, and worse than the library.
 *
 * And the order of the writes of a document saved as a whole: the last change has to be the last thing on disk, and
 * a write that finishes late must not put its older data back on screen.
 */
class BaseLocalDataRepositoryTest {

    @Test
    fun `a first read publishes what it has before it has all of it`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a"), listOf("a", "b")))

        repository.load()

        assertTrue(repository.states.any { it == DataState.Loading(listOf("a")) })
        assertEquals(DataState.Idle(listOf("a", "b")), repository.states.last())
    }

    @Test
    fun `a re-read keeps what is on screen instead of replacing it with a partial one`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a"), listOf("a", "b")))
        repository.load()
        repository.states.clear()

        repository.batches = listOf(listOf("c"), listOf("c", "d"))
        repository.reload()

        assertTrue(repository.states.none { it.data == listOf("c") })
        assertEquals(DataState.Idle(listOf("c", "d")), repository.states.last())
    }

    @Test
    fun `a re-read that fails falls back on the previous data rather than on a partial one`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a"), listOf("a", "b")))
        repository.load()

        repository.batches = listOf(listOf("c"), listOf("c", "d"))
        repository.shouldFail = true
        repository.reload()

        assertEquals(DataState.Failure(listOf("a", "b")), repository.states.last())
    }

    @Test
    fun `a first read that fails leaves nothing behind`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a"), listOf("a", "b")), shouldFail = true)

        repository.load()

        assertEquals(DataState.Failure<List<String>>(null), repository.states.last())
    }

    @Test
    fun `a first read that is cancelled leaves nothing behind`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a"), listOf("a", "b")))
        repository.gate = CompletableDeferred()

        val load = launch { repository.load() }
        runCurrent()
        assertEquals(DataState.Loading(listOf("a")), repository.states.last())
        load.cancelAndJoin()

        assertEquals(DataState.Loading<List<String>>(null), repository.states.last())
    }

    @Test
    fun `a cancelled first read is read again by the next caller`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a"), listOf("a", "b")))
        repository.gate = CompletableDeferred()
        val load = launch { repository.load() }
        runCurrent()
        load.cancelAndJoin()

        repository.gate = null

        assertEquals(listOf("a", "b"), repository.load())
        assertEquals(DataState.Idle(listOf("a", "b")), repository.states.last())
    }

    @Test
    fun `a change that landed during a cancelled first read does not pass for the library`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a"), listOf("a", "b")))
        repository.gate = CompletableDeferred()
        val load = launch { repository.load() }
        runCurrent()

        repository.add("x")
        assertEquals(DataState.Idle(listOf("a", "x")), repository.states.last())
        load.cancelAndJoin()

        assertEquals(DataState.Loading<List<String>>(null), repository.states.last())
        repository.gate = null
        assertEquals(listOf("a", "b"), repository.load())
    }

    @Test
    fun `a re-read that is cancelled keeps the library and what changed meanwhile`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a", "b")))
        repository.load()
        repository.gate = CompletableDeferred()
        repository.batches = listOf(listOf("c"), listOf("c", "d"))

        val reload = launch { repository.reload() }
        runCurrent()
        repository.add("x")
        reload.cancelAndJoin()

        assertEquals(DataState.Idle(listOf("a", "b", "x")), repository.states.last())
        assertTrue(repository.states.none { it.data == listOf("c") })
    }

    @Test
    fun `writes reach the storage one at a time and in order`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(emptyList()))
        val persisted = mutableListOf<List<String>>()
        val gate = CompletableDeferred<Unit>()
        var hasSecondWriteStarted = false

        launch { repository.write(A) { persisted += it; gate.await() } }
        runCurrent()
        launch { repository.write(B) { hasSecondWriteStarted = true; persisted += it } }
        runCurrent()

        assertEquals(listOf(A), persisted)
        assertFalse(hasSecondWriteStarted)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(A, B), persisted)
        assertEquals(DataState.Idle(B), repository.states.last())
    }

    @Test
    fun `a write that finishes late does not put its data back`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(emptyList()))
        val gate = CompletableDeferred<Unit>()

        launch { repository.write(A) { gate.await() } }
        runCurrent()
        launch { repository.write(B) {} }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(DataState.Loading(null), DataState.Idle(A), DataState.Idle(B)), repository.states)
    }

    @Test
    fun `changes made while the storage is busy end in one write`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(emptyList()))
        val persisted = mutableListOf<List<String>>()
        val gate = CompletableDeferred<Unit>()

        launch { repository.write(A) { persisted += it; gate.await() } }
        runCurrent()
        launch { repository.write(B) { persisted += it } }
        runCurrent()
        launch { repository.write(C) { persisted += it } }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(A, C), persisted)
        assertEquals(DataState.Idle(C), repository.states.last())
    }

    @Test
    fun `a change is published before the storage is free`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(emptyList()))
        val gate = CompletableDeferred<Unit>()

        launch { repository.write(A) { gate.await() } }
        runCurrent()
        launch { repository.write(B) {} }
        runCurrent()

        assertEquals(DataState.Idle(B), repository.states.last())
        gate.complete(Unit)
    }

    @Test
    fun `a write that fails keeps the change and reports it`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(emptyList()))
        val persisted = mutableListOf<List<String>>()

        repository.write(A) { throw IllegalStateException("The storage could not be written.") }
        assertEquals(DataState.Failure(A), repository.states.last())

        repository.write(A) { persisted += it }
        assertEquals(listOf(A), persisted)
        assertEquals(DataState.Idle(A), repository.states.last())
    }

    @Test
    fun `a write that fails says nothing about a newer change`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(emptyList()))
        val persisted = mutableListOf<List<String>>()
        val gate = CompletableDeferred<Unit>()

        launch { repository.write(A) { gate.await(); throw IllegalStateException("The storage could not be written.") } }
        runCurrent()
        launch { repository.write(B) { persisted += it } }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(repository.states.none { it is DataState.Failure })
        assertEquals(listOf(B), persisted)
        assertEquals(DataState.Idle(B), repository.states.last())
    }

    @Test
    fun `the same data is written once it has not been written before`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a", "b")))
        val persisted = mutableListOf<List<String>>()
        repository.load()

        repository.write(listOf("a", "b")) { persisted += it }
        repository.write(listOf("a", "b")) { persisted += it }

        assertEquals(listOf(listOf("a", "b")), persisted)
    }

    /** Publishes every batch but the last as partial data, the way the library scan hands its batches over. */
    private class TestRepository(
        scope: CoroutineScope,
        var batches: List<List<String>>,
        var shouldFail: Boolean = false,
    ) : BaseLocalDataRepository<List<String>>() {

        /**
         * Every state that was published, in order. The collector is unconfined so that it runs at the moment of
         * each publish: the state is a `StateFlow`, and one that only ran between suspensions would be handed the
         * last value alone, which is the one thing these tests are not about.
         */
        val states = mutableListOf<DataState<List<String>>>()

        init {
            scope.launch(Dispatchers.Unconfined) { dataState.collect { states += it } }
        }

        suspend fun load() = loadDataIfNeeded()

        suspend fun reload() = reloadData()

        suspend fun write(data: List<String>, persist: suspend (List<String>) -> Unit) = writeData(data, persist)

        /** Completed by the test to let a load past its partial publishes, so that it can be cancelled or changed under first. */
        var gate: CompletableDeferred<Unit>? = null

        fun add(item: String) = updateData { it.orEmpty() + item }

        override suspend fun loadDataFromLocalSource(): List<String> {
            batches.dropLast(1).forEach { publishPartialData(it) }
            gate?.await()
            if (shouldFail) throw IllegalStateException("The local source could not be read.")
            return batches.last()
        }
    }

    private companion object {
        val A = listOf("a")
        val B = listOf("b")
        val C = listOf("c")
    }
}
