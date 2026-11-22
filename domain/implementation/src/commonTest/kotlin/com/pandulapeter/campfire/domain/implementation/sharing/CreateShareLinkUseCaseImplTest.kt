/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.sharing

import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.ShareState
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.domain.implementation.SharedLinksRepositoryStub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreateShareLinkUseCaseImplTest {

    @Test
    fun `an item already shared answers its link without sharing it again`() = runTest {
        val existing = share(fileName = "Gig.setlist.json")
        val repository = FakeSharedLinksRepository(afterRefresh = listOf(existing))

        val result = CreateShareLinkUseCaseImpl(repository).invoke(SharedLinkKind.SETLIST, "gig.setlist.json", "Gig")

        assertEquals(CreateShareResult.Shared(existing), result)
        assertNull(repository.shared)
    }

    @Test
    fun `an item whose share lost its copy is shared anew`() = runTest {
        val repository = FakeSharedLinksRepository(afterRefresh = listOf(share(fileName = "gig.setlist.json", state = ShareState.COPY_MISSING)))

        CreateShareLinkUseCaseImpl(repository).invoke(SharedLinkKind.SETLIST, "gig.setlist.json", "Gig")

        assertEquals(Triple(SharedLinkKind.SETLIST, "gig.setlist.json", "Gig"), repository.shared)
    }

    @Test
    fun `what the share answers is answered - a missing item and a copy too large included`() = runTest {
        listOf(CreateShareResult.Missing, CreateShareResult.TooLarge, CreateShareResult.Failed(SharingFailure.UNKNOWN)).forEach { answer ->
            val result = CreateShareLinkUseCaseImpl(FakeSharedLinksRepository(answer = answer)).invoke(SharedLinkKind.SONG, "song.cho", "Song")

            assertEquals(answer, result)
        }
    }

    @Test
    fun `a list that cannot be read stops the share before anything is shared`() = runTest {
        val repository = FakeSharedLinksRepository(refreshFailure = SharingFailure.NETWORK)

        val result = CreateShareLinkUseCaseImpl(repository).invoke(SharedLinkKind.SONG, "song.cho", "Song")

        assertEquals(CreateShareResult.Failed(SharingFailure.NETWORK), result)
        assertNull(repository.shared)
    }

    private fun share(fileName: String, state: ShareState = ShareState.OK) = Share(
        shareId = "aaaaaaaaaaaaaaaa",
        kind = SharedLinkKind.SETLIST,
        fileName = fileName,
        title = "Gig",
        link = ShareLinks.create(kind = SharedLinkKind.SETLIST, shareId = "aaaaaaaaaaaaaaaa", fileId = "f", rlkey = "k", title = "Gig")!!,
        sharedAt = null,
        state = state,
    )

    /** Shows [afterRefresh] once refreshed, and keeps what it was asked to share, answering [answer]. */
    private class FakeSharedLinksRepository(
        private val afterRefresh: List<Share> = emptyList(),
        private val refreshFailure: SharingFailure? = null,
        private val answer: CreateShareResult = CreateShareResult.Failed(SharingFailure.UNKNOWN),
    ) : SharedLinksRepositoryStub() {

        override val shares = MutableStateFlow(emptyList<Share>())
        var shared: Triple<SharedLinkKind, String, String>? = null

        override suspend fun refresh() = refreshFailure.also { if (it == null) shares.value = afterRefresh }

        override suspend fun share(kind: SharedLinkKind, fileName: String, title: String): CreateShareResult {
            shared = Triple(kind, fileName, title)
            return answer
        }
    }
}
