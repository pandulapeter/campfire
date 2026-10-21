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
import com.pandulapeter.campfire.domain.api.sharing.GetSharedLinksUseCase
import com.pandulapeter.campfire.domain.api.sharing.LoadSharedLinksUseCase
import com.pandulapeter.campfire.domain.api.sharing.StopSharingEverythingUseCase
import com.pandulapeter.campfire.domain.api.sharing.StopSharingUseCase
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun TestScope.controller(stops: Stops): Pair<ShareLinkController, MessageSink> {
        val messages = MessageSink(backgroundScope)
        val controller = ShareLinkController(
            scope = backgroundScope,
            messageSink = messages,
            getSharedLinks = object : GetSharedLinksUseCase {
                override fun invoke() = flowOf(listOf(share))
            },
            createShareLink = Unused,
            stopSharing = stops,
            stopSharingEverything = stops,
            loadSharedLinks = Unused,
        )
        return controller to messages
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
