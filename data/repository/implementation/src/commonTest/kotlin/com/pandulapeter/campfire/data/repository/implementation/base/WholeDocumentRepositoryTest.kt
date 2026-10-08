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
 * The order of the writes of a document saved as a whole: the last change has to be the last thing on disk, and a write
 * that finishes late must not put its older data back on screen.
 */
class WholeDocumentRepositoryTest {

    @Test
    fun `writes reach the storage one at a time and in order`() = runTest {
        val repository = TestDocumentRepository(backgroundScope, batches = listOf(emptyList()))
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
        val repository = TestDocumentRepository(backgroundScope, batches = listOf(emptyList()))
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
        val repository = TestDocumentRepository(backgroundScope, batches = listOf(emptyList()))
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
        val repository = TestDocumentRepository(backgroundScope, batches = listOf(emptyList()))
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
        val repository = TestDocumentRepository(backgroundScope, batches = listOf(emptyList()))
        val persisted = mutableListOf<List<String>>()

        repository.write(A) { throw IllegalStateException("The storage could not be written.") }
        assertEquals(DataState.Failure(A), repository.states.last())

        repository.write(A) { persisted += it }
        assertEquals(listOf(A), persisted)
        assertEquals(DataState.Idle(A), repository.states.last())
    }

    @Test
    fun `a write that fails says nothing about a newer change`() = runTest {
        val repository = TestDocumentRepository(backgroundScope, batches = listOf(emptyList()))
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
        val repository = TestDocumentRepository(backgroundScope, batches = listOf(listOf("a", "b")))
        val persisted = mutableListOf<List<String>>()
        repository.load()

        repository.write(listOf("a", "b")) { persisted += it }
        repository.write(listOf("a", "b")) { persisted += it }

        assertEquals(listOf(listOf("a", "b")), persisted)
    }

    @Test
    fun `changes build on each other rather than on the copy a caller last saw`() = runTest {
        val repository = TestDocumentRepository(backgroundScope, batches = listOf(emptyList()))
        repository.load()
        val persisted = mutableListOf<List<String>>()
        val gate = CompletableDeferred<Unit>()

        launch { repository.change({ it + "a" }) { persisted += it; gate.await() } }
        runCurrent()
        launch { repository.change({ it + "b" }) { persisted += it } }
        runCurrent()
        launch { repository.change({ it + "c" }) { persisted += it } }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(listOf("a"), listOf("a", "b", "c")), persisted)
        assertEquals(DataState.Idle(listOf("a", "b", "c")), repository.states.last())
    }

    @Test
    fun `a change before anything has been read changes nothing`() = runTest {
        val repository = TestDocumentRepository(backgroundScope, batches = listOf(emptyList()))
        val persisted = mutableListOf<List<String>>()

        repository.change({ it + "a" }) { persisted += it }

        assertTrue(persisted.isEmpty())
        assertEquals(DataState.Loading<List<String>>(null), repository.states.last())
    }

    @Test
    fun `a change that changes nothing leaves a failed write on show`() = runTest {
        val repository = TestDocumentRepository(backgroundScope, batches = listOf(emptyList()))
        repository.write(A) { throw IllegalStateException("The storage could not be written.") }

        repository.change({ it }) { throw IllegalStateException("Nothing should have been written.") }

        assertEquals(DataState.Failure(A), repository.states.last())
    }

    /** A document that reads as the last of its [batches] and is written through whatever each test hands it. */
    private class TestDocumentRepository(
        scope: CoroutineScope,
        var batches: List<List<String>>,
    ) : WholeDocumentRepository<List<String>>() {

        override val logger = Logger.Standard

        /** Every state that was published, in order, collected unconfined so that no publish is conflated away. */
        val states = mutableListOf<DataState<List<String>>>()

        init {
            scope.launch(Dispatchers.Unconfined) { dataState.collect { states += it } }
        }

        suspend fun load() = loadDataIfNeeded()

        suspend fun write(data: List<String>, persist: suspend (List<String>) -> Unit) = writeData(data, persist)

        suspend fun change(transform: (List<String>) -> List<String>, persist: suspend (List<String>) -> Unit) =
            transformAndWriteData(transform, persist)

        override suspend fun loadDataFromLocalSource() = batches.last()
    }

    private companion object {
        val A = listOf("a")
        val B = listOf("b")
        val C = listOf("c")
    }
}
