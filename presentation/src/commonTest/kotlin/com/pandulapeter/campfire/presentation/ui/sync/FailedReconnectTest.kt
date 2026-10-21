/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.sync

import com.pandulapeter.campfire.data.model.sync.SyncAccount
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.SyncState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FailedReconnectTest {

    private val connected = SyncState.Connected(
        account = SyncAccount(providerId = SyncProviderId.DROPBOX, id = "dbid:1", displayName = "Someone", email = null),
        progress = null,
        lastSyncedAt = null,
        lastOutcome = null,
    )

    @Test
    fun `a reconnect of a working connection that answers not connected and was not cancelled has failed`() {
        assertTrue(isFailedReconnect(stateBefore = connected, isConnected = false, stateAfter = connected, isCancelled = false))
    }

    @Test
    fun `a cancelled reconnect has not failed`() {
        assertFalse(isFailedReconnect(stateBefore = connected, isConnected = false, stateAfter = connected, isCancelled = true))
    }

    @Test
    fun `a reconnect that ended connected has not failed`() {
        assertFalse(isFailedReconnect(stateBefore = connected, isConnected = true, stateAfter = connected, isCancelled = false))
    }

    @Test
    fun `a first connection that did not go through is no failed reconnect`() {
        assertFalse(
            isFailedReconnect(stateBefore = SyncState.Disconnected, isConnected = false, stateAfter = SyncState.Disconnected, isCancelled = false),
        )
    }

    @Test
    fun `a reconnect that ended in a failed connection is told by the connection itself`() {
        val failed = SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.UNKNOWN)

        assertFalse(isFailedReconnect(stateBefore = connected, isConnected = false, stateAfter = failed, isCancelled = false))
    }
}
