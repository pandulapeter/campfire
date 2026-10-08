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

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteFile
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
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

    private fun JsonElement.field(name: String) = (this as JsonObject)[name]
}
