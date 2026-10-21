/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.ShareState
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.domain.api.sharing.CreateShareLinkUseCase
import com.pandulapeter.campfire.domain.api.sharing.GetPendingShareStopsUseCase
import com.pandulapeter.campfire.domain.api.sharing.GetSharedLinksUseCase
import com.pandulapeter.campfire.domain.api.sharing.LoadSharedLinksUseCase
import com.pandulapeter.campfire.domain.api.sharing.StopSharingEverythingUseCase
import com.pandulapeter.campfire.domain.api.sharing.StopSharingUseCase
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareLinkControllerTest {

    private val share = Share(
        shareId = "abcdefghijklmnop",
        kind = SharedLinkKind.SONG,
        fileName = "song.cho",
        title = "Song",
        link = requireNotNull(ShareLinks.create(kind = SharedLinkKind.SONG, shareId = "abcdefghijklmnop", fileId = "fileid", rlkey = "rlkey", title = "Song")),
        sharedAt = null,
        state = ShareState.OK,
    )

    private val songA = ShareTarget(SharedLinkKind.SONG, "a.cho")

    private val songB = ShareTarget(SharedLinkKind.SONG, "b.cho")

    @Test
    fun `a share is listed as stopping until its stop is over, and a second request meanwhile is ignored`() = runTest {
        val answer = CompletableDeferred<SharingFailure?>()
        val stops = Stops(stop = { answer.await() })
        val (controller, messages) = controller(stops)

        controller.stop(share)
        controller.stop(share)
        runCurrent()

        assertEquals(setOf(share.shareId), controller.stoppingShareIds.value)
        answer.complete(null)
        runCurrent()
        assertEquals(emptySet(), controller.stoppingShareIds.value)
        assertEquals(1, stops.stopCount)
        assertTrue(messages.messageQueue.value.isEmpty())
    }

    @Test
    fun `a stop that fails says why and gives the share back`() = runTest {
        val (controller, messages) = controller(Stops(stop = { SharingFailure.NETWORK }))

        controller.stop(share)
        runCurrent()

        assertEquals(emptySet(), controller.stoppingShareIds.value)
        assertEquals(listOf<Message>(Message.StopSharingFailed(SharingFailure.NETWORK)), messages.messageQueue.value.map { it.value })
    }

    @Test
    fun `stopping everything that fails says why and gives every share back`() = runTest {
        val (controller, messages) = controller(Stops(stopEverything = { SharingFailure.AUTHORIZATION }))

        controller.stopEverything()
        assertTrue(controller.isStoppingEverything.value)
        runCurrent()

        assertFalse(controller.isStoppingEverything.value)
        assertEquals(
            listOf<Message>(Message.StopSharingEverythingFailed(SharingFailure.AUTHORIZATION)),
            messages.messageQueue.value.map { it.value },
        )
    }

    @Test
    fun `an item's link is made while another's is still being made, each ending in its own entry`() = runTest {
        val creations = Creations()
        val (controller, _) = controller(creations = creations)

        controller.create(songA, title = "A")
        runCurrent()
        controller.create(songB, title = "B")
        runCurrent()

        assertEquals(listOf("a.cho", "b.cho"), creations.fileNames)
        creations.answer("b.cho", CreateShareResult.TooLarge)
        creations.answer("a.cho", CreateShareResult.Missing)
        runCurrent()
        assertEquals(CreateShareResult.Missing, controller.creations.value[songA]?.result)
        assertEquals(CreateShareResult.TooLarge, controller.creations.value[songB]?.result)
    }

    @Test
    fun `a second create link for an item whose link is being made is ignored`() = runTest {
        val creations = Creations()
        val (controller, _) = controller(creations = creations)

        controller.create(songA, title = "A")
        runCurrent()
        controller.create(songA, title = "A")
        runCurrent()

        assertEquals(listOf("a.cho"), creations.fileNames)
    }

    @Test
    fun `forgetting a creation keeps one still running and drops one that is over`() = runTest {
        val creations = Creations()
        val (controller, _) = controller(creations = creations)

        controller.create(songA, title = "A")
        runCurrent()
        controller.forgetCreation(songA)
        assertEquals(ShareCreation(songA, result = null), controller.creations.value[songA])

        creations.answer("a.cho", CreateShareResult.TooLarge)
        runCurrent()
        controller.forgetCreation(songA)
        assertNull(controller.creations.value[songA])
    }

    @Test
    fun `whether stops are pending follows the use case`() = runTest {
        val pendingStops = MutableStateFlow(false)
        val (controller) = controller(pendingStops = pendingStops)
        runCurrent()
        assertFalse(controller.hasPendingStops.value)

        pendingStops.value = true
        runCurrent()

        assertTrue(controller.hasPendingStops.value)
    }

    private fun TestScope.controller(
        stops: Stops = Stops(),
        creations: CreateShareLinkUseCase = Unused,
        pendingStops: Flow<Boolean> = flowOf(false),
    ): Pair<ShareLinkController, MessageSink> {
        val messages = MessageSink(backgroundScope)
        val controller = ShareLinkController(
            scope = backgroundScope,
            messageSink = messages,
            getSharedLinks = object : GetSharedLinksUseCase {
                override fun invoke() = flowOf(listOf(share))
            },
            getPendingShareStops = object : GetPendingShareStopsUseCase {
                override fun invoke() = pendingStops
            },
            createShareLink = creations,
            stopSharing = stops,
            stopSharingEverything = stops,
            loadSharedLinks = Unused,
        )
        return controller to messages
    }

    /** Makes each link only once the test answers for its file. */
    private class Creations : CreateShareLinkUseCase {

        private val answers = mutableMapOf<String, CompletableDeferred<CreateShareResult>>()

        val fileNames = mutableListOf<String>()

        fun answer(fileName: String, result: CreateShareResult) = answers.getValue(fileName).complete(result)

        override suspend operator fun invoke(kind: SharedLinkKind, fileName: String, title: String): CreateShareResult {
            fileNames += fileName
            return answers.getOrPut(fileName) { CompletableDeferred() }.await()
        }
    }

    private class Stops(
        private val stop: suspend () -> SharingFailure? = { error("Not expected.") },
        private val stopEverything: suspend () -> SharingFailure? = { error("Not expected.") },
    ) : StopSharingUseCase, StopSharingEverythingUseCase {

        var stopCount = 0

        override suspend operator fun invoke(shareId: String): SharingFailure? {
            stopCount++
            return stop()
        }

        override suspend operator fun invoke() = stopEverything()
    }

    private object Unused : CreateShareLinkUseCase, LoadSharedLinksUseCase {
        override suspend operator fun invoke(kind: SharedLinkKind, fileName: String, title: String): CreateShareResult = error("Not expected.")
        override suspend operator fun invoke(): SharingFailure? = error("Not expected.")
    }
}
