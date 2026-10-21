/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.ShareState
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.data.model.sync.SyncAccount
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.presentation.ui.share.ShareCreation
import com.pandulapeter.campfire.presentation.ui.share.ShareTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkExportStateTest {

    private val target = ShareTarget(SharedLinkKind.SETLIST, "set.setlist.json")

    private val connected = SyncState.Connected(
        account = SyncAccount(SyncProviderId.DROPBOX, id = "id", displayName = "Name", email = null),
        progress = null,
        lastSyncedAt = null,
        lastOutcome = null,
    )

    @Test
    fun `without a connection the screen offers to connect, whatever is shared`() {
        val shares = listOf(share())
        assertEquals(LinkExportState.NotConnected, stateOf(SyncState.Disconnected, shares))
        assertEquals(LinkExportState.Connecting, stateOf(SyncState.Connecting(SyncProviderId.DROPBOX), shares))
        assertEquals(
            LinkExportState.ConnectionFailed(SyncFailureReason.NETWORK),
            stateOf(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.NETWORK), shares),
        )
        assertEquals(LinkAction.CONNECT, linkActionOf(LinkExportState.NotConnected))
        assertEquals(LinkAction.CANCEL, linkActionOf(LinkExportState.Connecting))
        assertEquals(LinkAction.CONNECT_AGAIN, linkActionOf(LinkExportState.ConnectionFailed(SyncFailureReason.NETWORK)))
    }

    @Test
    fun `an item the account lists is shared, whichever device shared it`() {
        val share = share()
        assertEquals(LinkExportState.Shared(share), stateOf(connected, listOf(share(fileName = "other.setlist.json"), share)))
        assertEquals(LinkAction.COPY, linkActionOf(LinkExportState.Shared(share)))
    }

    @Test
    fun `a song and a setlist of one file name are different items`() {
        assertEquals(LinkExportState.NotShared, stateOf(connected, listOf(share(kind = SharedLinkKind.SONG))))
        assertEquals(LinkAction.CREATE, linkActionOf(LinkExportState.NotShared))
    }

    @Test
    fun `a share too large to follow its item says so`() {
        assertTrue(assertIs<LinkExportState.Shared>(stateOf(connected, listOf(share(state = ShareState.TOO_LARGE)))).isTooLarge)
    }

    @Test
    fun `a share whose copy is gone says so and is replaced by a new one`() {
        val dead = share(state = ShareState.COPY_MISSING)
        assertEquals(LinkExportState.CopyMissing, stateOf(connected, listOf(dead)))
        assertEquals(LinkAction.CREATE, linkActionOf(LinkExportState.CopyMissing))
        assertEquals(LinkExportState.Creating, stateOf(connected, listOf(dead), creation = ShareCreation(target, result = null)))
        val replacement = share().copy(shareId = "replacement")
        assertEquals(
            LinkExportState.Shared(replacement),
            stateOf(connected, listOf(dead), creation = ShareCreation(target, CreateShareResult.Shared(replacement))),
        )
    }

    @Test
    fun `a share being made or stopped is work in progress with nothing to press`() {
        assertEquals(LinkExportState.Creating, stateOf(connected, creation = ShareCreation(target, result = null)))
        val share = share()
        assertEquals(LinkExportState.Stopping, stateOf(connected, listOf(share), stoppingShareIds = setOf(share.shareId)))
        assertEquals(LinkExportState.Stopping, stateOf(connected, creation = ShareCreation(target, CreateShareResult.Shared(share)), stoppingShareIds = setOf(share.shareId)))
        assertNull(linkActionOf(LinkExportState.Creating))
        assertNull(linkActionOf(LinkExportState.Stopping))
    }

    @Test
    fun `a share just made is shown before the account's list catches up`() {
        val share = share()
        assertEquals(LinkExportState.Shared(share), stateOf(connected, creation = ShareCreation(target, CreateShareResult.Shared(share))))
    }

    @Test
    fun `how sharing another item went is not this one's`() {
        val other = ShareTarget(SharedLinkKind.SONG, "a.cho")
        assertEquals(LinkExportState.NotShared, stateOf(connected, creation = ShareCreation(other, CreateShareResult.TooLarge)))
        assertEquals(LinkExportState.NotShared, stateOf(connected, creation = ShareCreation(other, result = null)))
    }

    @Test
    fun `a failure is told apart by what can be done about it`() {
        fun actionAfter(result: CreateShareResult) = linkActionOf(stateOf(connected, creation = ShareCreation(target, result)))
        assertEquals(LinkAction.CONNECT_AGAIN, actionAfter(CreateShareResult.Failed(SharingFailure.SCOPE_MISSING)))
        assertEquals(LinkAction.CONNECT_AGAIN, actionAfter(CreateShareResult.Failed(SharingFailure.AUTHORIZATION)))
        assertEquals(LinkAction.TRY_AGAIN, actionAfter(CreateShareResult.Failed(SharingFailure.NETWORK)))
        assertEquals(LinkAction.TRY_AGAIN, actionAfter(CreateShareResult.Failed(SharingFailure.REFUSED)))
        assertNull(actionAfter(CreateShareResult.TooLarge))
        assertNull(actionAfter(CreateShareResult.Missing))
        assertNull(actionAfter(CreateShareResult.Failed(SharingFailure.NOT_PUBLIC)))
        assertNull(actionAfter(CreateShareResult.Failed(SharingFailure.REMOTE_STORAGE_FULL)))
        assertNull(actionAfter(CreateShareResult.Failed(SharingFailure.NEWER_VERSION)))
        assertEquals(
            LinkExportState.Failed(LinkProblem.SCOPE_MISSING),
            stateOf(connected, creation = ShareCreation(target, CreateShareResult.Failed(SharingFailure.SCOPE_MISSING))),
        )
    }

    private fun stateOf(
        syncState: SyncState,
        shares: List<Share> = emptyList(),
        creation: ShareCreation? = null,
        stoppingShareIds: Set<String> = emptySet(),
    ) = linkExportStateOf(syncState = syncState, target = target, shares = shares, creation = creation, stoppingShareIds = stoppingShareIds)

    private fun share(
        kind: SharedLinkKind = target.kind,
        fileName: String = target.fileName,
        state: ShareState = ShareState.OK,
    ) = Share(
        shareId = "${kind.id}:$fileName",
        kind = kind,
        fileName = fileName,
        title = "Set",
        link = requireNotNull(ShareLinks.create(kind = kind, shareId = "abcdefghijklmnop", fileId = "fileid", rlkey = "rlkey", title = "Set")),
        sharedAt = null,
        state = state,
    )
}
