/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.scheduling

import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.model.sync.SyncOutcome
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.sync.implementation.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.TEST_ACCOUNT
import com.pandulapeter.campfire.data.sync.implementation.awaitOutcome
import com.pandulapeter.campfire.data.sync.implementation.song
import com.pandulapeter.campfire.data.sync.implementation.syncRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncRunStepsTest {

    private val events = mutableListOf<String>()

    @Test
    fun `the steps run in order inside the run lock once the files are in step`() = runTest {
        lateinit var first: RecordingRunStep
        lateinit var second: RecordingRunStep
        val repository = repository { runLock ->
            first = RecordingRunStep(id = "first", events = events, runLock = runLock)
            second = RecordingRunStep(id = "second", events = events, runLock = runLock)
            listOf(first, second)
        }

        repository.synchronize(SyncDeletionPolicy.ASK)
        val outcome = assertIs<SyncOutcome.Success>(repository.awaitOutcome().lastOutcome)

        assertEquals(listOf("run first", "run second"), events)
        assertEquals(listOf(true), first.heldLock)
        assertEquals(listOf(true), second.heldLock)
        assertEquals(setOf(song(1)), first.indexes.single().keys)
        assertEquals(emptySet(), outcome.summary.failedSteps)
    }

    @Test
    fun `a failed step is reported and the steps after it still run`() = runTest {
        val repository = repository {
            listOf(
                RecordingRunStep(id = "answers false", events = events, answer = { false }),
                RecordingRunStep(id = "throws", events = events, answer = { throw IllegalStateException("unreadable") }),
                RecordingRunStep(id = "works", events = events),
            )
        }

        repository.synchronize(SyncDeletionPolicy.ASK)
        val outcome = assertIs<SyncOutcome.Success>(repository.awaitOutcome().lastOutcome)

        assertEquals(listOf("run answers false", "run throws", "run works"), events)
        assertEquals(setOf("answers false", "throws"), outcome.summary.failedSteps)
        assertEquals(emptyList(), outcome.summary.failed)
    }

    @Test
    fun `a step that ends the run fails it and the steps after it do not run`() = runTest {
        val repository = repository {
            listOf(
                RecordingRunStep(id = "offline", events = events, answer = { throw SyncNetworkException("offline") }),
                RecordingRunStep(id = "after", events = events),
            )
        }

        repository.synchronize(SyncDeletionPolicy.ASK)

        assertEquals(SyncOutcome.Failure(SyncFailureReason.NETWORK), repository.awaitOutcome().lastOutcome)
        assertEquals(listOf("run offline"), events)
    }

    @Test
    fun `disconnecting forgets every step inside the run lock`() = runTest {
        lateinit var steps: List<RecordingRunStep>
        val repository = repository { runLock ->
            steps = listOf("first", "second").map { RecordingRunStep(id = it, events = events, runLock = runLock) }
            steps
        }

        repository.disconnect()

        assertIs<SyncState.Disconnected>(repository.syncState.first())
        assertEquals(listOf("forget first", "forget second"), events)
        assertTrue(steps.all { it.heldLock == listOf(true) })
    }

    private suspend fun TestScope.repository(
        steps: (runLock: suspend (suspend () -> Unit) -> Unit) -> List<SyncRunStep>,
    ) = syncRepository(
        provider = FakeSyncProvider(files = mapOf(song(1) to ONE), account = TEST_ACCOUNT),
        libraryFileLocalSource = FakeLibraryFileLocalSource(files = mapOf(song(1) to ONE)),
        extraSteps = steps,
    ).also { it.restore {} }

    private companion object {
        val ONE = "One".encodeToByteArray()
    }
}
