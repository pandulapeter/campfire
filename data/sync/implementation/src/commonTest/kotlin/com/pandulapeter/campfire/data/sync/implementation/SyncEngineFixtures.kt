/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.local.api.SetlistComparison
import com.pandulapeter.campfire.data.source.remote.api.SyncFolder
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash

/** The account the engine tests' indexes belong to, as [SyncIndexDocument.accountId] files it. */
internal const val ACCOUNT_ID = "dropbox:someone@example.com"

internal val ORIGINAL = "Original".encodeToByteArray()
internal val HERE = "Edited here".encodeToByteArray()
internal val THERE = "Edited there".encodeToByteArray()

internal val GIG = SyncKey(kind = LibraryFileKind.SETLIST, name = "gig.setlist.json")
internal val GIG_COPY = SyncKey(kind = LibraryFileKind.SETLIST, name = "gig (2).setlist.json")

internal fun song(number: Int) = song(name = "song_$number")

internal fun song(name: String) = SyncKey(kind = LibraryFileKind.SONG, name = "$name.cho")

internal fun foreign(name: String) = SyncKey(kind = LibraryFileKind.SONG, name = name)

/** A file one byte over what a run reads, put into the library from outside the app. */
internal fun tooLarge() = ByteArray((ImportLimits.MAX_TEXT_FILE_SIZE + 1).toInt())

internal fun librarySongs(count: Int) = (1..count).associate { song(it) to "Song $it".encodeToByteArray() }

/** An index that says the last run saw [files] with these contents, at the revision the fake starts from. */
internal fun indexOf(vararg files: Pair<SyncKey, ByteArray>) = SyncIndexDocument.of(
    providerId = SyncProviderId.DROPBOX.id,
    accountId = ACCOUNT_ID,
    lastSyncedAt = 1,
    syncedPreferences = null,
    index = files.associate { (key, bytes) ->
        key to SyncIndexEntry(localHash = localContentHash(bytes), remoteRevision = "r0")
    },
)

/** One file in step with the fake's revision rather than [indexOf]'s, for the tests that rename it. */
internal fun renamedIndexOf(file: Pair<SyncKey, ByteArray>, revision: String) = SyncIndexDocument.of(
    providerId = SyncProviderId.DROPBOX.id,
    accountId = ACCOUNT_ID,
    lastSyncedAt = 1,
    syncedPreferences = null,
    index = mapOf(file.first to SyncIndexEntry(localHash = localContentHash(file.second), remoteRevision = revision)),
)

/** An index that says the last run saw [files] as they are, on this device and at the revision the fake holds. */
internal fun syncedIndexOf(files: Map<SyncKey, ByteArray>) = SyncIndexDocument.of(
    providerId = SyncProviderId.DROPBOX.id,
    accountId = ACCOUNT_ID,
    lastSyncedAt = 1,
    syncedPreferences = null,
    index = files.mapValues { (_, bytes) -> SyncIndexEntry(localHash = localContentHash(bytes), remoteRevision = "r1") },
)

/**
 * One run of a [SyncEngine] built for it, as [ACCOUNT_ID]'s, answering every question with [SyncDeletionPolicy.ASK]
 * unless a test says otherwise. Everything else a test needs to see or change is a parameter, so that each test names
 * only what it is about.
 */
internal suspend fun synchronize(
    local: LibraryFileLocalSource,
    provider: SyncFolder,
    document: SyncIndexDocument,
    setlistComparison: SetlistComparison = NoSetlistComparison,
    lock: LibraryFileLock = LibraryFileLock(),
    plantedContentHash: suspend (SyncKey) -> String? = { null },
    logger: Logger = Logger.Standard,
    accountId: String = ACCOUNT_ID,
    onIndexChanged: suspend (snapshot: () -> SyncIndexDocument) -> Unit = {},
    onLocalFileChanged: suspend (SyncKey) -> Unit = {},
    deletionPolicy: SyncDeletionPolicy = SyncDeletionPolicy.ASK,
) = SyncEngine(
    libraryFileLocalSource = local,
    libraryFileLock = lock,
    setlistComparison = setlistComparison,
    plantedContentHash = plantedContentHash,
    logger = logger,
).synchronize(
    provider = provider,
    document = document,
    accountId = accountId,
    onProgress = {},
    onIndexChanged = onIndexChanged,
    onLocalFileChanged = onLocalFileChanged,
    deletionPolicy = deletionPolicy,
)

/**
 * A save the way the song and setlist repositories make one: under [lock], from what it checks the file against to
 * its write, which goes straight into the map rather than through the hooks a test stands in the engine's own
 * writes with. The repositories' half, that they take the lock at all, is tested in `SongRepositoryImplTest` and
 * `SetlistRepositoryImplTest`.
 */
internal suspend fun saveUnderTheLock(lock: LibraryFileLock, local: FakeLibraryFileLocalSource, key: SyncKey, bytes: ByteArray) =
    lock.withLock { local.files[key] = bytes }
