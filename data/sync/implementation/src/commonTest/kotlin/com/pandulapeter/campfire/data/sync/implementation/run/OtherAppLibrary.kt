/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.library.SyncCollection
import com.pandulapeter.campfire.data.model.library.SyncCollections
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder
import com.pandulapeter.campfire.data.sync.implementation.ACCOUNT_ID
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexDocument
import kotlin.test.assertIs

/**
 * The library of an app that is not Campfire, a notes app's, which the engine has to serve by its seams alone: notes
 * name the files they show by a `see:<name>;` token ([references]), and a checklist's `ticked:…;` token is filled in by
 * every device on its own ([conflictPolicies]). Nothing here is read the way Campfire reads its songs or setlists.
 */
internal object OtherAppLibrary {

    val NOTES = SyncCollection(id = "notes", matches = { it.endsWith(".note") }, maxSize = MAX_SIZE, role = SyncCollection.Role.DOCUMENT)

    val CHECKLISTS = SyncCollection(id = "checklists", matches = { it.endsWith(".list") }, maxSize = MAX_SIZE, role = SyncCollection.Role.DOCUMENT)

    val BLOBS = SyncCollection(
        id = "blobs",
        matches = LibraryFiles::isLibraryFileName,
        maxSize = MAX_SIZE,
        role = SyncCollection.Role.CONTENT_FILE,
    )

    val collections = SyncCollections(all = listOf(NOTES, CHECKLISTS, BLOBS))

    private val named = Regex("see:([^;]+);")

    val references = mapOf(
        NOTES to SyncReferences { document -> named.findAll(document.decodeToString()).mapTo(mutableSetOf()) { it.groupValues[1] } },
    )

    private val ticked = Regex("ticked:[^;]*;")

    val conflictPolicies = mapOf<SyncCollection, ConflictPolicy>(
        CHECKLISTS to object : ConflictPolicy {

            override fun isSameApartFromVolatileParts(first: ByteArray, second: ByteArray) =
                first.decodeToString().replace(ticked, "") == second.decodeToString().replace(ticked, "")

            override fun withoutVolatileParts(bytes: ByteArray) = bytes.decodeToString().replace(ticked, "").encodeToByteArray()
        },
    )

    fun note(name: String) = SyncKey(kind = NOTES, name = "$name.note")

    fun checklist(name: String) = SyncKey(kind = CHECKLISTS, name = "$name.list")

    /** A content file named for [bytes], as the engine checks every transfer of one against. */
    fun blob(bytes: ByteArray) = SyncKey(kind = BLOBS, name = localContentHash(bytes).take(16) + ".png")

    /** An index that says the last run saw [files] as they are, here and at the revision the fake holds. */
    fun syncedIndexOf(vararg files: Pair<SyncKey, ByteArray>) = SyncIndexDocument.of(
        providerId = SyncProviderId.DROPBOX.id,
        accountId = ACCOUNT_ID,
        lastSyncedAt = 1,
        syncedPreferences = null,
        index = files.associate { (key, bytes) -> key to SyncIndexEntry(localHash = localHashOf(key, bytes), remoteRevision = "r1") },
    )

    /** One completed run of an engine built over this library alone. */
    suspend fun synchronize(local: LibraryFileLocalSource, provider: SyncFolder, document: SyncIndexDocument) =
        assertIs<SyncEngine.Result.Completed>(
            SyncEngine(
                libraryFileLocalSource = local,
                libraryFileLock = LibraryFileLock(),
                references = references,
                conflictPolicies = conflictPolicies,
                plantedContentHash = { null },
                logger = Logger.Standard,
                collections = collections,
                largeFileThreshold = MAX_SIZE,
                imageWritesGate = { block -> block(emptySet()) },
            ).synchronize(
                provider = provider,
                document = document,
                accountId = ACCOUNT_ID,
                onProgress = {},
                onIndexChanged = {},
                onLocalFileChanged = {},
                deletionPolicy = SyncDeletionPolicy.ASK,
            ),
        )

    private const val MAX_SIZE = 1_000_000L
}
