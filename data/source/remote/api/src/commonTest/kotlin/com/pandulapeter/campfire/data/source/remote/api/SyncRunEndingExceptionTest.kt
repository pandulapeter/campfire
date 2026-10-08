/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.model.domain.SyncFailureReason
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncRunEndingExceptionTest {

    @Test
    fun `every exception that ends a run says what the run ended in`() {
        assertEquals(SyncFailureReason.AUTHORIZATION, reasonOf(SyncAuthorizationException("")))
        assertEquals(SyncFailureReason.NETWORK, reasonOf(SyncNetworkException("")))
        assertEquals(SyncFailureReason.REMOTE_STORAGE_FULL, reasonOf(SyncRemoteStorageFullException("")))
    }

    /** Exhaustive on purpose: a fourth subclass fails to compile here until it is added above. */
    private fun reasonOf(exception: SyncRunEndingException) = when (exception) {
        is SyncAuthorizationException -> exception.reason
        is SyncNetworkException -> exception.reason
        is SyncRemoteStorageFullException -> exception.reason
    }
}
