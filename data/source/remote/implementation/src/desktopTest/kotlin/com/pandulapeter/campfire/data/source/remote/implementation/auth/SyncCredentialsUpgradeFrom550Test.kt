/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.sync.PendingAuthorization
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reads the `sync-credentials.json` Campfire 5.5.0 left behind on the desktop and the web (`resources/upgrade-5.5.0`,
 * written by that version's own credentials store): one connected to Dropbox, and one holding only an authorization
 * the web build started before navigating away to ask for consent.
 */
class SyncCredentialsUpgradeFrom550Test {

    private val fixture = File(requireNotNull(javaClass.getResource("/upgrade-5.5.0")).toURI())

    private fun storeOf(state: String) =
        SyncCredentialsStore(StoredSyncCredentials(File(fixture, "$state/preferences/sync-credentials.json").readText()), Logger.Standard)

    @Test
    fun `the credentials 5_5_0 left connected keep every token and the account they belong to`() = runTest {
        val store = storeOf("connected")

        assertEquals(
            SyncCredentialsDocument(
                providerId = "dropbox",
                accessToken = "sl.u.AFixtureAccessToken550",
                refreshToken = "FixtureRefreshToken550",
                expiresAt = 1795000000000,
                accountId = "dbid:AAFixtureAccount550",
                displayName = "Ada Lovelace",
                email = "ada@example.com",
            ),
            store.load(),
        )
        assertEquals(SyncProviderId.DROPBOX, store.storedProviderId())
        assertNull(PendingAuthorizationStoreImpl(store).loadPendingAuthorization())
    }

    @Test
    fun `the credentials 5_5_0 left mid-authorization keep the authorization and belong to no provider yet`() = runTest {
        val store = storeOf("pending")

        assertEquals(
            PendingAuthorization(
                providerId = SyncProviderId.DROPBOX,
                state = "FixtureState550",
                verifier = "FixtureVerifier550",
                redirectUri = "https://campfire-songbook.com/app/",
            ),
            PendingAuthorizationStoreImpl(store).loadPendingAuthorization(),
        )
        assertNull(store.storedProviderId())
    }
}
