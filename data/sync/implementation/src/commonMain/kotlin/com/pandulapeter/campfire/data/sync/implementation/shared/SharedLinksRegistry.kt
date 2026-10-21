/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteWriteResult
import com.pandulapeter.campfire.data.sync.implementation.SyncEnvironment
import org.koin.core.annotation.Single

/**
 * The account's list of shared links in its cloud folder ([SharedLinksFormat.NAME]), read and edited through
 * [SharedLinksMirror], which every read and successful edit replaces.
 *
 * Edited only as operations: read, change, and write back against the revision that was read, and on a conflict
 * (another device, or a run, wrote in between) all of it again with what that wrote, up to [MAX_ATTEMPTS] times. That
 * needs no lock, the run's included, and no merge: each change is small and idempotent, so applying it again to the
 * newer list is always right. Service failures are thrown, as the folder throws them.
 */
@Single
internal class SharedLinksRegistry(
    private val mirror: SharedLinksMirror,
    private val environment: SyncEnvironment,
) {

    sealed interface Read {

        /**
         * The list, and the revision it was read at; null where there is none yet. A list that cannot be read is an
         * empty one at its revision, so a sharing action writes over it, re-reading first as every edit does.
         */
        data class Readable(val revision: String?, val document: SharedLinksDocument) : Read

        /** A later version's list, which this version neither shows nor edits. */
        data class FromNewerVersion(val version: Int) : Read
    }

    /**
     * What an edit does with the list it read: [document] to write, or null to write nothing, and the [result] the
     * edit answers either way.
     */
    data class Change<T>(val document: SharedLinksDocument?, val result: T)

    sealed interface Edit<out T> {

        data class Done<T>(val result: T) : Edit<T>

        data class FromNewerVersion(val version: Int) : Edit<Nothing>

        /** Every attempt met another write. */
        data object Contended : Edit<Nothing>
    }

    suspend fun read(folder: SyncFolder): Read {
        val remote = folder.downloadDocument(SharedLinksFormat.NAME)
            ?: return Read.Readable(revision = null, document = SharedLinksDocument()).also { mirror.save(null, it.document) }
        val document = when (val decoded = SharedLinksFormat.decode(remote.bytes.decodeToString())) {
            is SharedLinksFormat.Decoded.Readable -> decoded.document
            is SharedLinksFormat.Decoded.FromNewerVersion -> return Read.FromNewerVersion(decoded.version)
            SharedLinksFormat.Decoded.Unreadable -> {
                environment.logger.log("The shared links document in the cloud folder cannot be read; it counts as empty.")
                SharedLinksDocument()
            }
        }
        mirror.save(remote.revision, document)
        return Read.Readable(revision = remote.revision, document = document)
    }

    suspend fun <T> edit(folder: SyncFolder, change: (SharedLinksDocument) -> Change<T>): Edit<T> {
        repeat(MAX_ATTEMPTS) {
            val read = when (val read = read(folder)) {
                is Read.Readable -> read
                is Read.FromNewerVersion -> return Edit.FromNewerVersion(read.version)
            }
            val (document, result) = change(read.document)
            if (document == null) return Edit.Done(result)
            when (val written = folder.uploadDocument(SharedLinksFormat.NAME, SharedLinksFormat.encode(document).encodeToByteArray(), read.revision)) {
                is RemoteWriteResult.Written -> {
                    mirror.save(written.revision, document)
                    return Edit.Done(result)
                }

                RemoteWriteResult.Conflict -> Unit
            }
        }
        environment.logger.log("The shared links document kept changing under an edit; gave up after $MAX_ATTEMPTS attempts.")
        return Edit.Contended
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
