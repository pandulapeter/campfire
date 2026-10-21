/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.ShareState
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.data.model.sync.SyncAccount
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharingStageTest {

    private val connected = SyncState.Connected(
        account = SyncAccount(providerId = SyncProviderId.DROPBOX, id = "id", displayName = "Name", email = null),
        progress = null,
        lastSyncedAt = null,
        lastOutcome = null,
    )

    @Test
    fun `without a working connection the tab invites to connect, whatever this device last read`() {
        val shares = listOf(share("aaaaaaaaaaaaaaaa"))
        assertEquals(SharingStage.NOT_CONNECTED, sharingStageOf(SyncState.Disconnected, refreshFailure = null, shares = shares))
        assertEquals(
            SharingStage.NOT_CONNECTED,
            sharingStageOf(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.NETWORK), refreshFailure = null, shares = shares),
        )
        assertEquals(SharingStage.CONNECTING, sharingStageOf(SyncState.Connecting(SyncProviderId.DROPBOX), refreshFailure = null, shares = shares))
    }

    @Test
    fun `a connection without the permission to make links asks to connect again`() {
        assertEquals(SharingStage.CONNECT_AGAIN, sharingStageOf(connected, SharingFailure.SCOPE_MISSING, emptyList()))
        assertEquals(SharingStage.CONNECT_AGAIN, sharingStageOf(connected, SharingFailure.AUTHORIZATION, listOf(share("aaaaaaaaaaaaaaaa"))))
    }

    @Test
    fun `a list that could not be read afresh still shows the one last read`() {
        val shares = listOf(share("aaaaaaaaaaaaaaaa"))
        assertEquals(SharingStage.SHARED, sharingStageOf(connected, SharingFailure.NETWORK, shares))
        assertEquals(SharingStage.NOTHING_SHARED, sharingStageOf(connected, SharingFailure.NETWORK, emptyList()))
        assertEquals(SharingStage.SHARED, sharingStageOf(connected, SharingFailure.NEWER_VERSION, shares))
    }

    @Test
    fun `a list read afresh, replaced, or explained elsewhere is not called stale`() {
        assertFalse(isStaleSharingList(null))
        assertFalse(isStaleSharingList(SharingFailure.SCOPE_MISSING))
        assertFalse(isStaleSharingList(SharingFailure.AUTHORIZATION))
        assertFalse(isStaleSharingList(SharingFailure.NEWER_VERSION))
        assertTrue(isStaleSharingList(SharingFailure.NETWORK))
        assertTrue(isStaleSharingList(SharingFailure.UNKNOWN))
    }

    @Test
    fun `a stopped share stays in its place among the rows until it has left`() {
        val (a, b, c) = listOf(share("aaaaaaaaaaaaaaaa"), share("bbbbbbbbbbbbbbbb"), share("cccccccccccccccc"))

        assertEquals(listOf(a, b, c), sharedLinkRowsOf(shown = listOf(a, b, c), shares = listOf(a, c)))
        assertEquals(listOf(a, b, c), sharedLinkRowsOf(shown = listOf(a, b, c), shares = listOf(b, c)))
        assertEquals(listOf(a, b), sharedLinkRowsOf(shown = listOf(a), shares = listOf(b)))
    }

    @Test
    fun `a share arriving with a fresher list is a row of its own`() {
        val (a, b) = listOf(share("aaaaaaaaaaaaaaaa"), share("bbbbbbbbbbbbbbbb"))

        assertEquals(listOf(b, a), sharedLinkRowsOf(shown = listOf(a), shares = listOf(b, a)))
    }

    private fun share(shareId: String) = Share(
        shareId = shareId,
        kind = SharedLinkKind.SONG,
        fileName = "$shareId.cho",
        title = shareId,
        link = assertNotNull(ShareLinks.create(SharedLinkKind.SONG, shareId = shareId, fileId = "file", rlkey = "key", title = shareId)),
        sharedAt = null,
        state = ShareState.OK,
    )
}
