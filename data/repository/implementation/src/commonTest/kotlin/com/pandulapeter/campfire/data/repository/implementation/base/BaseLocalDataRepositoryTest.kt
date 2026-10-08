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
import com.pandulapeter.campfire.data.model.domain.Logger
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
        assertEquals(DataState.Loading(listOf("a", "x")), repository.states.last())
        load.cancelAndJoin()

        assertEquals(DataState.Loading<List<String>>(null), repository.states.last())
        repository.gate = null
        assertEquals(listOf("a", "b"), repository.load())
    }

    @Test
    fun `a change during a first read is not published as the library`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a"), listOf("a", "b")))
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        launch { repository.load() }
        runCurrent()

        repository.add("x")
        // The change is on disk by now, which is where the read that follows finds it.
        repository.batches = listOf(listOf("a"), listOf("a", "b", "x"))
        runCurrent()
        assertTrue(repository.states.none { it is DataState.Idle })

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(DataState.Idle(listOf("a", "b", "x")), repository.states.last())
    }

    @Test
    fun `a partial publish after a change keeps the change`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a"), listOf("a", "b"), listOf("a", "b", "c")))
        val partialGate = CompletableDeferred<Unit>()
        repository.partialGate = partialGate
        launch { repository.load() }
        runCurrent()
        assertEquals(DataState.Loading(listOf("a")), repository.states.last())

        repository.add("x")
        val publishedAfterChange = repository.states.size - 1
        partialGate.complete(Unit)
        advanceUntilIdle()

        // Up to the end of the first read: the re-read the change asks for reads the source, which the test never wrote "x" to.
        val loadingStates = repository.states.drop(publishedAfterChange).takeWhile { it is DataState.Loading }
        assertTrue(loadingStates.size >= 2)
        assertTrue(loadingStates.all { it.data.orEmpty().count { item -> item == "x" } == 1 })
    }

    @Test
    fun `a change before the first read is not passed off as the library`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a"), listOf("a", "b")))

        repository.add("x")
        assertEquals(DataState.Loading(listOf("x")), repository.states.last())

        assertEquals(listOf("a", "b"), repository.load())
        assertEquals(DataState.Idle(listOf("a", "b")), repository.states.last())
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
    fun `a change that lands during a read makes it read once more`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a")))
        repository.load()
        repository.onRead = { if (repository.reads == 1) repository.add("x") }

        repository.reload()

        assertEquals(2, repository.reads)
        assertEquals(DataState.Idle(listOf("a")), repository.states.last())
    }

    @Test
    fun `changes that keep landing during reads are applied rather than read again`() = runTest {
        val repository = TestRepository(backgroundScope, batches = listOf(listOf("a")))
        repository.load()
        repository.onRead = { repository.add("x${repository.reads}") }

        val result = repository.reload()

        assertEquals(2, repository.reads)
        assertEquals(listOf("a", "x2"), result)
        assertEquals(DataState.Idle(listOf("a", "x2")), repository.states.last())
    }

    /** Publishes every batch but the last as partial data, the way the library scan hands its batches over. */
    private class TestRepository(
        scope: CoroutineScope,
        var batches: List<List<String>>,
        var shouldFail: Boolean = false,
    ) : BaseLocalDataRepository<List<String>>() {

        override val logger = Logger.Standard

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

        /** Completed by the test to let a load past its partial publishes, so that it can be cancelled or changed under first. */
        var gate: CompletableDeferred<Unit>? = null

        /** A change the way real callers make one, safe to apply again: the entry is replaced rather than added twice. */
        fun add(item: String) = updateData { it.orEmpty().filterNot { existing -> existing == item } + item }

        /** Completed by the test to let a load past its first partial publish, so that a change can land between two. */
        var partialGate: CompletableDeferred<Unit>? = null

        /** How many times the local source was read since [onRead] was last set. */
        var reads = 0

        /** Called as each read lists the local source, which is where a change that lands during a read lands. */
        var onRead: (() -> Unit)? = null
            set(value) {
                field = value
                reads = 0
            }

        override suspend fun loadDataFromLocalSource(): List<String> {
            reads++
            onRead?.invoke()
            batches.dropLast(1).forEachIndexed { index, batch ->
                if (index == 1) partialGate?.await()
                publishPartialData(batch)
            }
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
