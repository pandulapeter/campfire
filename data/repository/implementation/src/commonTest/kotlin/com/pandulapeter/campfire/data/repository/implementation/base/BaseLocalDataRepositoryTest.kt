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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

        override suspend fun loadDataFromLocalSource(): List<String> {
            batches.dropLast(1).forEach { publishPartialData(it) }
            if (shouldFail) throw IllegalStateException("The local source could not be read.")
            return batches.last()
        }
    }
}
