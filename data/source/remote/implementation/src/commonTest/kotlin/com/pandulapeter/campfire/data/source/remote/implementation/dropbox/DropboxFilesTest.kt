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
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncDocumentTooLargeException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteFile
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteWriteResult
import com.pandulapeter.campfire.data.source.remote.implementation.crypto.dropboxContentHash
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** What the provider asks Dropbox for when it lists, writes and reads files, and how it reads the answers. */
class DropboxFilesTest {

    @Test
    fun `a listing follows its cursor and reports only the files of the two library folders`() = runTest {
        val requests = mutableListOf<Pair<String, String>>()
        val provider = dropboxProvider { request ->
            requests += request.url.encodedPath to (request.body as TextContent).text
            if (requests.size == 1) {
                respondJson(
                    """{"entries":[
                        {".tag":"folder","name":"songs","path_lower":"/songs"},
                        {".tag":"file","name":"Wonderwall.cho","path_lower":"/songs/wonderwall.cho","rev":"r1","size":10,"content_hash":"h1"},
                        {".tag":"deleted","name":"gone.cho","path_lower":"/songs/gone.cho"},
                        {".tag":"file","name":"preferences.json","path_lower":"/preferences.json","rev":"r2"},
                        {".tag":"file","name":"x.cho","path_lower":"/songs/sub/x.cho","rev":"r3"}
                    ],"cursor":"next page","has_more":true}""",
                )
            } else {
                respondJson(
                    """{"entries":[
                        {".tag":"file","name":"summer.setlist.json","path_lower":"/setlists/summer.setlist.json","rev":"r4","size":20},
                        {".tag":"file","name":"notes.txt","path_lower":"/notes/notes.txt","rev":"r5"}
                    ],"cursor":"","has_more":false}""",
                )
            }
        }

        val listing = provider.list()

        assertEquals(
            expected = listOf(
                RemoteFile(LibraryFileKind.SONG, name = "Wonderwall.cho", revision = "r1", contentHash = "h1", size = 10),
                RemoteFile(LibraryFileKind.SETLIST, name = "summer.setlist.json", revision = "r4", contentHash = null, size = 20),
            ),
            actual = listing.files,
        )
        assertEquals(listOf("/2/files/list_folder", "/2/files/list_folder/continue"), requests.map { it.first })
        assertEquals(JsonPrimitive("next page"), Json.parseToJsonElement(requests[1].second).field("cursor"))
    }

    @Test
    fun `an upload with an expected revision asks Dropbox to update exactly that revision`() = runTest {
        var argument: JsonElement? = null
        val provider = dropboxProvider { request ->
            argument = Json.parseToJsonElement(request.headers["Dropbox-API-Arg"].orEmpty())
            respondJson("""{"name":"song.cho","rev":"r6"}""")
        }

        val result = provider.upload(LibraryFileKind.SONG, "song.cho", byteArrayOf(1), expectedRevision = "r5")

        assertEquals(RemoteWriteResult.Written("r6"), result)
        assertEquals(JsonPrimitive("/songs/song.cho"), argument?.field("path"))
        assertEquals(JsonObject(mapOf(".tag" to JsonPrimitive("update"), "update" to JsonPrimitive("r5"))), argument?.field("mode"))
        assertEquals(JsonPrimitive(false), argument?.field("autorename"))
    }

    /** A name Dropbox invented would be a song nobody asked for. */
    @Test
    fun `an upload of a new file asks Dropbox to add it under its own name or not at all`() = runTest {
        var argument: JsonElement? = null
        val provider = dropboxProvider { request ->
            argument = Json.parseToJsonElement(request.headers["Dropbox-API-Arg"].orEmpty())
            respondJson("""{"name":"song.cho","rev":"r1"}""")
        }

        provider.upload(LibraryFileKind.SONG, "song.cho", byteArrayOf(1), expectedRevision = null)

        assertEquals(JsonObject(mapOf(".tag" to JsonPrimitive("add"))), argument?.field("mode"))
        assertEquals(JsonPrimitive(false), argument?.field("autorename"))
    }

    @Test
    fun `an upload the file moved under is a conflict rather than a failure`() = runTest {
        val provider = dropboxProvider {
            respondJson("""{"error_summary":"path/conflict/file/..","error":{}}""", HttpStatusCode.Conflict)
        }
        assertEquals(
            expected = RemoteWriteResult.Conflict,
            actual = provider.upload(LibraryFileKind.SONG, "song.cho", byteArrayOf(1), expectedRevision = "r5"),
        )
    }

    @Test
    fun `a download answers the revision it fetched`() = runTest {
        val provider = dropboxProvider {
            respond(content = "song", status = HttpStatusCode.OK, headers = headersOf("Dropbox-API-Result", """{"rev":"r7"}"""))
        }
        val downloaded = provider.download(LibraryFileKind.SONG, "song.cho")
        assertEquals("song", downloaded.bytes.decodeToString())
        assertEquals("r7", downloaded.revision)
    }

    @Test
    fun `a download that names no revision fails`() = runTest {
        val provider = dropboxProvider { respond(content = "song", status = HttpStatusCode.OK) }
        assertFailsWith<DropboxApiException> { provider.download(LibraryFileKind.SONG, "song.cho") }
    }

    /** An empty object from something in between would otherwise read as an empty folder, and every song as deleted. */
    @Test
    fun `a listing answered with an empty object is the network failing`() = runTest {
        for (answer in listOf("{}", """{"error":"blocked"}""")) {
            val provider = dropboxProvider { respondJson(answer) }
            assertFailsWith<SyncNetworkException>(answer) { provider.list() }
        }
    }

    @Test
    fun `a listing page with more to come but no cursor is the network failing`() = runTest {
        val provider = dropboxProvider { respondJson("""{"entries":[],"cursor":"","has_more":true}""") }
        assertFailsWith<SyncNetworkException> { provider.list() }
    }

    @Test
    fun `an upload receipt without a revision is the network failing`() = runTest {
        for (receipt in listOf("""{"name":"song.cho"}""", """{"rev":""}""")) {
            val provider = dropboxProvider { respondJson(receipt) }
            assertFailsWith<SyncNetworkException>(receipt) { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }
        }
    }

    /** A body cut short without a declared length would otherwise be written as the song and indexed as in step. */
    @Test
    fun `a download shorter than the file Dropbox describes fails`() = runTest {
        val provider = dropboxProvider { downloadAnswer("""{"rev":"r7","size":4000}""") }
        assertFailsWith<DropboxApiException> { provider.download(LibraryFileKind.SONG, "song.cho") }
    }

    @Test
    fun `a download whose bytes do not hash to Dropbox's hash fails`() = runTest {
        val hash = dropboxContentHash("sing".encodeToByteArray())
        val provider = dropboxProvider { downloadAnswer("""{"rev":"r7","size":4,"content_hash":"$hash"}""") }
        assertFailsWith<DropboxApiException> { provider.download(LibraryFileKind.SONG, "song.cho") }
    }

    @Test
    fun `a download that matches its size and hash is accepted`() = runTest {
        val hash = dropboxContentHash("song".encodeToByteArray())
        val provider = dropboxProvider { downloadAnswer("""{"rev":"r7","size":4,"content_hash":"$hash"}""") }
        val downloaded = provider.download(LibraryFileKind.SONG, "song.cho")
        assertEquals("song", downloaded.bytes.decodeToString())
        assertEquals("r7", downloaded.revision)
    }

    @Test
    fun `a document shorter than the file Dropbox describes fails`() = runTest {
        val provider = dropboxProvider { downloadAnswer("""{"rev":"r7","size":4000}""") }
        assertFailsWith<DropboxApiException> { provider.downloadDocument("preferences.json") }
    }

    @Test
    fun `a document that is not there is no document rather than a failure`() = runTest {
        var argument: JsonElement? = null
        val provider = dropboxProvider { request ->
            argument = Json.parseToJsonElement(request.headers["Dropbox-API-Arg"].orEmpty())
            respondJson("""{"error_summary":"path/not_found/..","error":{}}""", HttpStatusCode.Conflict)
        }
        assertNull(provider.downloadDocument("preferences.json"))
        assertEquals(JsonPrimitive("/preferences.json"), argument?.field("path"))
    }

    @Test
    fun `a document Dropbox describes as larger than the limit is refused with its revision without reading its body`() = runTest {
        var requests = 0
        val provider = dropboxProvider {
            requests++
            respond(
                content = unreadableBody(),
                status = HttpStatusCode.OK,
                headers = headersOf("Dropbox-API-Result", """{"rev":"r1","size":${DropboxDownloads.MAXIMUM_DOWNLOAD_SIZE + 1}}"""),
            )
        }

        val refusal = assertFailsWith<SyncDocumentTooLargeException> { provider.downloadDocument("preferences.json") }

        assertEquals("r1", refusal.revision)
        assertEquals(1, requests)
    }

    @Test
    fun `a document whose body runs past the limit without a declared size is refused with its revision`() = runTest {
        var requests = 0
        val provider = dropboxProvider {
            requests++
            respond(content = oversizedBody(), status = HttpStatusCode.OK, headers = headersOf("Dropbox-API-Result", """{"rev":"r1"}"""))
        }

        val refusal = assertFailsWith<SyncDocumentTooLargeException> { provider.downloadDocument("preferences.json") }

        assertEquals("r1", refusal.revision)
        assertEquals(1, requests)
    }

    @Test
    fun `a library file larger than the limit is one file's failure, asked once`() = runTest {
        val declared = """{"rev":"r1","size":${DropboxDownloads.MAXIMUM_DOWNLOAD_SIZE + 1}}"""
        val answers = listOf<MockRequestHandleScope.() -> HttpResponseData>(
            { respond(content = unreadableBody(), status = HttpStatusCode.OK, headers = headersOf("Dropbox-API-Result", declared)) },
            { respond(content = oversizedBody(), status = HttpStatusCode.OK, headers = headersOf("Dropbox-API-Result", """{"rev":"r1"}""")) },
        )
        for (answer in answers) {
            var requests = 0
            val provider = dropboxProvider {
                requests++
                answer()
            }

            assertFailsWith<DropboxApiException> { provider.download(LibraryFileKind.SONG, "song.cho") }
            assertEquals(1, requests)
        }
    }

    private fun JsonElement.field(name: String) = (this as JsonObject)[name]

    /** A download of the body `song`, described by [metadata] as Dropbox's `Dropbox-API-Result` header. */
    private fun MockRequestHandleScope.downloadAnswer(metadata: String) =
        respond(content = "song", status = HttpStatusCode.OK, headers = headersOf("Dropbox-API-Result", metadata))

    /** A body one byte past the limit. */
    private fun oversizedBody() = ByteArray(DropboxDownloads.MAXIMUM_DOWNLOAD_SIZE.toInt() + 1)

    /** A body that fails the moment anything reads it, so a refusal that reads it is retried and ends as a network failure. */
    private fun unreadableBody(): ByteReadChannel = ByteChannel().apply { cancel(IllegalStateException("The body was read.")) }
}
