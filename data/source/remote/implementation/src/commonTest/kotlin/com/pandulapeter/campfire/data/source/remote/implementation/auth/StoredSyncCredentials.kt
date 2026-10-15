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

import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.api.SyncCredentialsLocalSource
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * The credentials document in a variable, as the platform storage would keep it. Its next read can be made to end the
 * way a cleared view model ends it or the way a secret store that refuses for a moment does, and its next write can be
 * refused, or cancelled once it has gone through.
 */
internal class StoredSyncCredentials(var credentials: String? = null) : SyncCredentialsLocalSource {
    var shouldCancelNextRead = false
    var shouldFailNextRead = false
    var shouldRefuseNextWrite = false
    var shouldCancelNextWriteAfterStoring = false
    var readCount = 0
    var writeAttemptCount = 0

    /** [credentials] read back the way the provider reads them. */
    val document get() = credentials?.let { Json { ignoreUnknownKeys = true }.decodeFromString<SyncCredentialsDocument>(it) }

    override suspend fun loadSyncCredentials(): String? {
        readCount++
        if (shouldCancelNextRead) {
            shouldCancelNextRead = false
            throw CancellationException("The reader went away.")
        }
        if (shouldFailNextRead) {
            shouldFailNextRead = false
            throw LibraryStorageException("Keystore busy")
        }
        return credentials
    }

    override suspend fun saveSyncCredentials(document: String?) {
        writeAttemptCount++
        if (shouldRefuseNextWrite) {
            shouldRefuseNextWrite = false
            throw IllegalStateException("The storage refused the write.")
        }
        credentials = document
        if (shouldCancelNextWriteAfterStoring) {
            shouldCancelNextWriteAfterStoring = false
            throw CancellationException("The writer went away.")
        }
    }
}

/**
 * A stored Dropbox connection whose access token is good for as long as any test runs unless [expiresAt] says
 * otherwise, so no request needs a refresh. [extraFields] are appended to the document as they are, each starting
 * with a comma.
 */
internal fun connectedCredentials(expiresAt: Long = Long.MAX_VALUE, extraFields: String = "") =
    """{"providerId":"dropbox","accessToken":"access","refreshToken":"refresh","expiresAt":$expiresAt$extraFields}"""
