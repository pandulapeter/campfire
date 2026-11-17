/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncScopeMissingException
import com.pandulapeter.campfire.data.source.remote.implementation.network.toAsciiJsonString

/**
 * Deleted library files brought back from Dropbox's own history, for [DropboxSyncProvider.restoreDeleted]. Dropbox
 * keeps a deleted file's revisions for 30 days (longer on some plans), and `files/restore` puts one back as the file's
 * newest revision. Restoring the top revision of a deleted file can be answered `invalid_revision` while the one below
 * it works, so up to [MAXIMUM_TRIES] revisions are tried, newest first.
 *
 * Whatever Dropbox refuses answers false: a path with no history, revisions it will not restore, a token without the
 * scope, which is logged once rather than for every file, since only connecting again changes it.
 */
internal class DropboxDeletedFiles(
    private val requests: DropboxTransport,
    private val logger: Logger,
) {

    private var isMissingScopeLogged = false

    suspend fun restore(path: String): Boolean = try {
        val revisions = requests.rpc(
            url = LIST_REVISIONS_URL,
            body = """{"path":${path.toAsciiJsonString()},"mode":"path","limit":$MAXIMUM_TRIES}""",
            deserializer = DropboxListRevisionsResponse.serializer(),
        )
        // Written again since it was listed: the folder holds the name, and the next listing names it.
        if (revisions.isDeleted) restoreAny(path, revisions.entries.take(MAXIMUM_TRIES).map { it.rev }) else revisions.entries.isNotEmpty()
    } catch (exception: DropboxApiException) {
        logger.log("Dropbox restored nothing for \"$path\": ${exception.errorSummary}")
        false
    } catch (exception: SyncScopeMissingException) {
        if (!isMissingScopeLogged) logger.log("Dropbox restores no deleted file: ${exception.message}")
        isMissingScopeLogged = true
        false
    }

    private suspend fun restoreAny(path: String, revisions: List<String>): Boolean {
        revisions.forEach { revision ->
            try {
                requests.rpc(
                    url = RESTORE_URL,
                    body = """{"path":${path.toAsciiJsonString()},"rev":${revision.toAsciiJsonString()}}""",
                    deserializer = DropboxFileMetadata.serializer(),
                )
                return true
            } catch (exception: DropboxApiException) {
                if (exception.statusCode != CONFLICT || !exception.errorSummary.startsWith("invalid_revision")) throw exception
            }
        }
        logger.log("Dropbox restored no revision of \"$path\".")
        return false
    }

    private companion object {
        const val LIST_REVISIONS_URL = "https://api.dropboxapi.com/2/files/list_revisions"
        const val RESTORE_URL = "https://api.dropboxapi.com/2/files/restore"
        const val MAXIMUM_TRIES = 3
        const val CONFLICT = 409
    }
}
