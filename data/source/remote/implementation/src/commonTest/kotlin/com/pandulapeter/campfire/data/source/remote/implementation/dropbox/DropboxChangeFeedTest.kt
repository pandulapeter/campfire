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

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteChanges
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteFile
import com.pandulapeter.campfire.data.source.remote.implementation.network.configureClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ClosedReadChannelException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How the provider watches the app folder: the listing's cursor, the long poll on it, and the changes since it. */
class DropboxChangeFeedTest {

    @Test
    fun `a listing names the cursor of its last page`() = runTest {
        val provider = dropboxProvider { respondJson("""{"entries":[],"cursor":"after everything","has_more":false}""") }

        assertEquals("after everything", provider.list().cursor)
    }

    @Test
    fun `a listing names the revision of each document at the top of the folder`() = runTest {
        var pages = 0
        val provider = dropboxProvider {
            pages++
            if (pages == 1) {
                respondJson(
                    """{"entries":[
                        {".tag":"file","name":"preferences.json","path_lower":"/preferences.json","rev":"r9"},
                        {".tag":"folder","name":"songs","path_lower":"/songs"}
                    ],"cursor":"page 2","has_more":true}""",
                )
            } else {
                respondJson(
                    """{"entries":[
                        {".tag":"file","name":"a.cho","path_lower":"/songs/a.cho","rev":"r1","size":1,"content_hash":"h1"},
                        {".tag":"file","name":"notes.txt","path_lower":"/notes.txt"}
                    ],"cursor":"page 3","has_more":false}""",
                )
            }
        }

        val listing = provider.list()

        assertEquals(mapOf("preferences.json" to "r9"), listing.documentRevisions)
        assertEquals(listOf("a.cho"), listing.files.map { it.name })
    }

    @Test
    fun `a listing without a cursor names none`() = runTest {
        val provider = dropboxProvider { respondJson(EMPTY_LISTING) }

        assertNull(provider.list().cursor)
    }

    @Test
    fun `waiting for changes sends the cursor without a token and answers what Dropbox says`() = runTest {
        var url = ""
        var body: JsonObject? = null
        var authorization: String? = "unread"
        val provider = dropboxProvider(configure = { install(HttpTimeout) }) { request ->
            url = request.url.toString()
            body = Json.parseToJsonElement((request.body as TextContent).text) as JsonObject
            authorization = request.headers["Authorization"]
            respondJson("""{"changes":true}""")
        }

        assertTrue(provider.awaitChanges("cursor 1"))
        assertEquals("https://notify.dropboxapi.com/2/files/list_folder/longpoll", url)
        assertEquals(JsonPrimitive("cursor 1"), body?.get("cursor"))
        assertNull(authorization)
    }

    @Test
    fun `a wait that ends without changes says so`() = runTest {
        val provider = dropboxProvider(configure = { install(HttpTimeout) }) { respondJson("""{"changes":false}""") }

        assertFalse(provider.awaitChanges("cursor 1"))
    }

    @Test
    fun `a wait cut off without an answer is the network failing, and is sent once`() = runTest {
        var requests = 0
        val provider = dropboxProvider(configure = { install(HttpTimeout) }) {
            requests++
            throw ClosedReadChannelException()
        }

        assertFailsWith<SyncNetworkException> { provider.awaitChanges("cursor 1") }
        assertEquals(1, requests)
    }

    @Test
    fun `a stalled wait fails within the call's own bound`() = runTest {
        var requests = 0
        val provider = dropboxProvider(
            configure = { configureClient(sendsUserAgent = false, hasSocketTimeout = false) },
            engineDispatcher = StandardTestDispatcher(testScheduler),
        ) {
            requests++
            awaitCancellation()
        }

        assertFailsWith<SyncNetworkException> { provider.awaitChanges("cursor 1") }
        assertEquals(1, requests)
        assertTrue(currentTime <= DropboxChangeFeed.LONGPOLL_CALL_TIMEOUT_MILLIS + 1_000, "The wait took $currentTime ms to fail.")
    }

    @Test
    fun `a wait Dropbox asks for is waited before answering`() = runTest {
        val provider = dropboxProvider(configure = { install(HttpTimeout) }) { respondJson("""{"changes":false,"backoff":30}""") }

        provider.awaitChanges("cursor 1")

        assertTrue(currentTime >= 30_000)
    }

    @Test
    fun `a change reported with a wait Dropbox asks for is answered at once and the next poll waits`() = runTest {
        var polls = 0
        val provider = dropboxProvider(configure = { install(HttpTimeout) }) {
            polls++
            respondJson(if (polls == 1) """{"changes":true,"backoff":30}""" else """{"changes":false}""")
        }

        assertTrue(provider.awaitChanges("cursor 1"))
        assertEquals(0L, currentTime)

        provider.awaitChanges("cursor 2")

        assertTrue(currentTime >= 30_000)
        assertEquals(2, polls)
    }

    @Test
    fun `a cursor Dropbox no longer takes is answered as a change`() = runTest {
        val provider = dropboxProvider(configure = { install(HttpTimeout) }) {
            respondJson("""{"error_summary":"reset/..","error":{".tag":"reset"}}""", status = HttpStatusCode.Conflict)
        }

        assertTrue(provider.awaitChanges("cursor 1"))
    }

    @Test
    fun `the changes since a cursor follow its pages and are sorted into files, deletions, and documents`() = runTest {
        val cursors = mutableListOf<String>()
        val provider = dropboxProvider { request ->
            val cursor = (Json.parseToJsonElement((request.body as TextContent).text) as JsonObject)["cursor"]
            cursors += (cursor as JsonPrimitive).content
            if (cursors.size == 1) {
                respondJson(
                    """{"entries":[
                        {".tag":"file","name":"Wonderwall.cho","path_lower":"/songs/wonderwall.cho","rev":"r1","size":10,"content_hash":"h1"},
                        {".tag":"deleted","name":"gone.cho","path_lower":"/songs/gone.cho"},
                        {".tag":"deleted","name":"x.cho","path_lower":"/songs/sub/x.cho"}
                    ],"cursor":"page 2","has_more":true}""",
                )
            } else {
                respondJson(
                    """{"entries":[
                        {".tag":"file","name":"preferences.json","path_lower":"/preferences.json","rev":"r2"},
                        {".tag":"deleted","name":"setlists","path_lower":"/setlists"},
                        {".tag":"folder","name":"notes","path_lower":"/notes"}
                    ],"cursor":"page 3","has_more":false}""",
                )
            }
        }

        val changes = provider.changesSince("page 1")

        assertEquals(
            expected = RemoteChanges(
                files = listOf(RemoteFile(LibraryFileKind.SONG, name = "Wonderwall.cho", revision = "r1", contentHash = "h1", size = 10)),
                deletedFiles = listOf(RemoteChanges.DeletedFile(LibraryFileKind.SONG, name = "gone.cho")),
                documents = setOf("preferences.json", "setlists"),
                cursor = "page 3",
            ),
            actual = changes,
        )
        assertEquals(listOf("page 1", "page 2"), cursors)
    }
}
