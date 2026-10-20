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

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteChanges
import com.pandulapeter.campfire.data.source.remote.implementation.network.BrowserCallTimeout
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.toAsciiJsonString
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay

/** The app folder's changes since a cursor, for [DropboxSyncProvider.awaitChanges] and [DropboxSyncProvider.changesSince]. */
internal class DropboxChangeFeed(
    private val httpClientHolder: HttpClientHolder,
    private val requests: DropboxTransport,
) {

    /**
     * `list_folder/longpoll`, which takes no token: the cursor is all it goes by. Dropbox holds the request for
     * [LONGPOLL_TIMEOUT_SECONDS] plus the minute and a half of its own that Dropbox documents (104 s was measured), so
     * the call's own bounds are raised past that, or the silence of a folder nobody touches would read as a dead
     * connection. It is sent once and never retried here: what a poll that ends without an answer means is the watcher's
     * call (`SyncRemoteWatcher`), which asks again at once after one the service held.
     */
    suspend fun awaitChanges(cursor: String): Boolean {
        val response = transport {
            httpClientHolder.client().post(LONGPOLL_URL) {
                attributes.put(BrowserCallTimeout, LONGPOLL_CALL_TIMEOUT_MILLIS)
                timeout { socketTimeoutMillis = LONGPOLL_CALL_TIMEOUT_MILLIS }
                contentType(ContentType.Application.Json)
                setBody("""{"cursor":${cursor.toAsciiJsonString()},"timeout":$LONGPOLL_TIMEOUT_SECONDS}""")
            }
        }
        if (response.status == HttpStatusCode.Conflict && response.errorSummary().startsWith("reset")) return true
        response.ensureSuccessful()
        val answer = decodeAnswer(DropboxLongpollResponse.serializer(), transport { response.bodyAsText() })
        answer.backoffSeconds?.let { delay(it * 1000L) }
        return answer.changes
    }

    suspend fun changesSince(cursor: String): RemoteChanges {
        val entries = mutableListOf<DropboxEntry>()
        var next = cursor
        do {
            val response = requests.rpc(
                url = DropboxSyncProvider.LIST_FOLDER_CONTINUE_URL,
                body = """{"cursor":${next.toAsciiJsonString()}}""",
                deserializer = DropboxListFolderResponse.serializer(),
                browserTimeoutMillis = DropboxTransport.LISTING_BROWSER_TIMEOUT_MILLIS,
            )
            entries += response.entries
            if (response.cursor.isEmpty()) throw SyncNetworkException("Dropbox's list of changes named no cursor.")
            next = response.cursor
        } while (response.hasMore)
        return RemoteChanges(
            files = entries.toRemoteFiles(),
            deletedFiles = entries.filter { it.tag == "deleted" }.mapNotNull { entry ->
                entry.libraryKind()?.let { kind -> RemoteChanges.DeletedFile(kind = kind, name = entry.name) }
            },
            // A deletion at the top of the folder is named here whatever it was: a library folder deleted whole may
            // arrive as that one entry rather than as one per file.
            documents = entries.filter { it.tag != "folder" && it.depth == 1 }.mapTo(mutableSetOf()) { it.name },
            cursor = next,
        )
    }

    internal companion object {
        const val LONGPOLL_URL = "https://notify.dropboxapi.com/2/files/list_folder/longpoll"

        /**
         * How long Dropbox is asked to hold a `longpoll` open when nothing changes: about fifteen small requests an hour
         * while the app is in front. Not Dropbox's maximum of eight minutes, since a connection that sits silent for
         * longer than about five is dropped by many mobile carriers' address translation without either side hearing
         * of it, and a change would then wait for the call's own bound below to be noticed.
         */
        const val LONGPOLL_TIMEOUT_SECONDS = 240

        /**
         * The wait Dropbox was asked for, the minute and a half it documents adding, with half a minute for the network,
         * which also covers the 104 s once measured.
         */
        const val LONGPOLL_CALL_TIMEOUT_MILLIS = (LONGPOLL_TIMEOUT_SECONDS + 90 + 30) * 1000L
    }
}
