/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationRequest
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationResponse
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteFile
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteListing
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult

/**
 * A remote folder held in memory, for running [SyncEngine] against. Revisions are a counter per write, which is all
 * the engine may assume about them, and the content hash is the local one so that identical bytes compare equal.
 *
 * [onDownload] and [onUpload] run before a transfer answers, which is where a test makes the service fail part way
 * through a run or changes the local library under an operation that is already under way. [sizes] lets a listing
 * report a large file without the test allocating it. [account] is who the service says is connected, which is what
 * a repository test restores the connection from. [ignoresCase] makes it a service like Dropbox, which takes two names
 * that differ only by case for one file, [listCount] is how many passes a run made and [downloadCounts] how many times
 * each file was fetched. [connected] says whether credentials are stored, and [onDisconnect] runs once a disconnect
 * has cleared them; [hasForgottenCredentials] says whether they were dropped without one, which does not run it.
 */
internal class FakeSyncProvider(
    files: Map<SyncKey, ByteArray> = emptyMap(),
    private val sizes: Map<SyncKey, Long> = emptyMap(),
    var onDownload: suspend (SyncKey) -> Unit = {},
    var onUpload: (SyncKey) -> Unit = {},
    private val account: SyncAccount? = null,
    private val ignoresCase: Boolean = false,
) : SyncProvider {

    var listCount = 0

    var connected = true

    var onDisconnect: suspend () -> Unit = {}

    var hasForgottenCredentials = false

    val downloadCounts = mutableMapOf<SyncKey, Int>()

    val files = files.mapValues { (_, bytes) -> bytes to "r1" }.toMutableMap()
    private var nextRevision = 2

    override val id = SyncProviderId.DROPBOX

    override suspend fun isConnected() = connected

    override fun buildAuthorizationRequest(redirectUri: String?) = RemoteAuthorizationRequest(
        authorizationUrl = "https://example.com/authorize",
        redirectUri = redirectUri,
        state = "state",
        verifier = "verifier",
    )

    override suspend fun completeAuthorization(
        response: RemoteAuthorizationResponse,
        verifier: String,
        redirectUri: String?,
    ): SyncAccount = throw UnsupportedOperationException()

    override suspend fun disconnect() {
        connected = false
        onDisconnect()
    }

    override suspend fun forgetStoredCredentials() {
        connected = false
        hasForgottenCredentials = true
    }

    override suspend fun loadAccount() = account

    override suspend fun storedAccount() = null

    override suspend fun list() = RemoteListing(
        files = files.also { listCount++ }.map { (key, file) ->
            RemoteFile(
                kind = key.kind,
                name = key.name,
                revision = file.second,
                contentHash = contentHashOf(file.first),
                size = sizes[key] ?: file.first.size.toLong(),
            )
        },
    )

    override suspend fun download(kind: LibraryFileKind, name: String): ByteArray {
        val key = SyncKey(kind = kind, name = name)
        downloadCounts[key] = (downloadCounts[key] ?: 0) + 1
        onDownload(key)
        return files.getValue(stored(key)).first
    }

    override fun contentHashOf(bytes: ByteArray) = localContentHash(bytes)

    /** Where a request lands: on a service that ignores case, the stored spelling of the name, as Dropbox keeps it. */
    private fun stored(key: SyncKey) = if (ignoresCase) {
        files.keys.firstOrNull { it.kind == key.kind && it.name.equals(key.name, ignoreCase = true) } ?: key
    } else {
        key
    }

    override suspend fun upload(
        kind: LibraryFileKind,
        name: String,
        bytes: ByteArray,
        expectedRevision: String?,
    ): RemoteWriteResult {
        val key = SyncKey(kind = kind, name = name)
        onUpload(key)
        val target = stored(key)
        if (files[target]?.second != expectedRevision) return RemoteWriteResult.Conflict
        val isHeldInAnotherCase = files.keys.any { it.kind == kind && it.name.lowercase() == name.lowercase() }
        if (expectedRevision == null && ignoresCase && isHeldInAnotherCase) return RemoteWriteResult.Conflict
        val revision = "r${nextRevision++}"
        files[target] = bytes to revision
        return RemoteWriteResult.Written(revision)
    }

    override suspend fun delete(kind: LibraryFileKind, name: String, expectedRevision: String?) {
        files -= stored(SyncKey(kind = kind, name = name))
    }
}
