/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When connecting and starting up begin a sync run of their own, which is the only thing these use cases decide. */
class SyncUseCaseImplsTest {

    private val sync = FakeSyncRepository()
    private val synchronizeLibrary = SynchronizeLibraryUseCaseImpl(sync)

    @Test
    fun `a start up that finds the account connected starts a run`() = runTest {
        sync.restored = SyncRepository.RestoreResult(isConnected = true, didReturnFromAuthorization = true, wasInterrupted = false)

        assertTrue(RestoreSyncUseCaseImpl(sync, synchronizeLibrary).invoke())
        assertEquals(1, sync.runs)
    }

    @Test
    fun `a start up after a run that was cut short leaves the message about it in place`() = runTest {
        sync.restored = SyncRepository.RestoreResult(isConnected = true, didReturnFromAuthorization = false, wasInterrupted = true)

        assertFalse(RestoreSyncUseCaseImpl(sync, synchronizeLibrary).invoke())
        assertEquals(0, sync.runs)
    }

    @Test
    fun `a start up that finds no account starts no run`() = runTest {
        sync.restored = SyncRepository.RestoreResult(isConnected = false, didReturnFromAuthorization = true, wasInterrupted = false)

        assertTrue(RestoreSyncUseCaseImpl(sync, synchronizeLibrary).invoke())
        assertEquals(0, sync.runs)
    }

    @Test
    fun `connecting starts a run and a failed connection does not`() = runTest {
        val connect = ConnectSyncProviderUseCaseImpl(sync, synchronizeLibrary)

        sync.isConnecting = false
        assertFalse(connect(SyncProviderId.DROPBOX, COMPLETION_PAGE))
        assertEquals(0, sync.runs)

        sync.isConnecting = true
        assertTrue(connect(SyncProviderId.DROPBOX, COMPLETION_PAGE))
        assertEquals(1, sync.runs)
    }

    private class FakeSyncRepository : SyncRepositoryStub() {
        var restored = SyncRepository.RestoreResult(isConnected = false, didReturnFromAuthorization = false, wasInterrupted = false)
        var isConnecting = true
        var runs = 0

        override suspend fun restore() = restored

        override suspend fun connect(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage) = isConnecting

        override fun synchronize(deletionPolicy: SyncDeletionPolicy): Boolean {
            runs++
            return true
        }
    }

    private companion object {
        val COMPLETION_PAGE = AuthorizationCompletionPage(title = "Connected", message = "You can return to Campfire.")
    }
}
