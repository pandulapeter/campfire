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
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteSharedFile
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteWriteResult
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.sync.model.SharedLinkResult
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The shared copies: where they are written, how their links are made, and where the listing reports them. */
class DropboxSharingTest {

    @Test
    fun `a shared copy is written under its id in the shared folder with the rules of any upload`() = runTest {
        var argument: JsonElement? = null
        val provider = dropboxProvider { request ->
            argument = Json.parseToJsonElement(request.headers["Dropbox-API-Arg"].orEmpty())
            respondJson("""{"name":"$SHARE_ID.zip","rev":"r2"}""")
        }

        val result = provider.uploadSharedFile(SHARE_ID, byteArrayOf(1), expectedRevision = "r1")

        assertEquals(RemoteWriteResult.Written("r2"), result)
        assertEquals(JsonPrimitive("/shared/$SHARE_ID.zip"), argument?.field("path"))
        assertEquals(JsonObject(mapOf(".tag" to JsonPrimitive("update"), "update" to JsonPrimitive("r1"))), argument?.field("mode"))
        assertEquals(JsonPrimitive(false), argument?.field("autorename"))
        assertEquals(JsonPrimitive(true), argument?.field("mute"))
    }

    @Test
    fun `a full Dropbox refuses a shared copy as it refuses a song`() = runTest {
        val provider = dropboxProvider {
            respondJson("""{"error_summary":"path/insufficient_space/..","error":{}}""", HttpStatusCode.Conflict)
        }
        assertFailsWith<SyncRemoteStorageFullException> { provider.uploadSharedFile(SHARE_ID, byteArrayOf(1), expectedRevision = null) }
    }

    /** It comes from a registry anyone can edit by hand, and must never address a library file. */
    @Test
    fun `a share id that is not a plain name is refused before any request`() = runTest {
        val provider = dropboxProvider { error("No request was expected.") }
        assertFailsWith<IllegalArgumentException> { provider.uploadSharedFile("../songs/song", byteArrayOf(1), expectedRevision = null) }
        assertFailsWith<IllegalArgumentException> { provider.createSharedLink("") }
        assertFailsWith<IllegalArgumentException> { provider.deleteSharedFile("a/b") }
    }

    @Test
    fun `a link is asked for public and view only and is read into its parts`() = runTest {
        var body: JsonElement? = null
        val provider = dropboxProvider { request ->
            body = Json.parseToJsonElement((request.body as TextContent).text)
            respondLink(visibility = "public")
        }

        assertEquals(SharedLinkResult.Created(provider = SyncProviderId.DROPBOX, parts = listOf(FILE_ID, RLKEY)), provider.createSharedLink(SHARE_ID))
        assertEquals(JsonPrimitive("/shared/$SHARE_ID.zip"), body?.field("path"))
        val settings = body?.field("settings")
        assertEquals(JsonPrimitive("public"), settings?.field("requested_visibility"))
        assertEquals(JsonPrimitive("public"), settings?.field("audience"))
        assertEquals(JsonPrimitive("viewer"), settings?.field("access"))
    }

    /** With settings sent, Dropbox's answer carries no metadata, so the existing link is listed instead. */
    @Test
    fun `a copy that has a link already keeps the one it has`() = runTest {
        val paths = mutableListOf<String>()
        val provider = dropboxProvider { request ->
            paths += request.url.encodedPath
            if (request.url.encodedPath.endsWith("create_shared_link_with_settings")) {
                respondJson(
                    """{"error_summary":"shared_link_already_exists/..","error":{".tag":"shared_link_already_exists"}}""",
                    HttpStatusCode.Conflict,
                )
            } else {
                respondJson("""{"links":[${linkJson(visibility = "public")}],"has_more":false}""")
            }
        }

        assertEquals(SharedLinkResult.Created(provider = SyncProviderId.DROPBOX, parts = listOf(FILE_ID, RLKEY)), provider.createSharedLink(SHARE_ID))
        assertEquals(listOf("/2/sharing/create_shared_link_with_settings", "/2/sharing/list_shared_links"), paths)
    }

    @Test
    fun `a link only the team can open is not public`() = runTest {
        val provider = dropboxProvider { respondLink(visibility = "team_only") }
        assertEquals(SharedLinkResult.NotPublic, provider.createSharedLink(SHARE_ID))
    }

    @Test
    fun `a link whose audience alone is named is judged by it`() = runTest {
        val provider = dropboxProvider {
            respondJson("""{"url":"$LINK_URL","link_permissions":{"effective_audience":{".tag":"public"}}}""")
        }
        assertEquals(SharedLinkResult.Created(provider = SyncProviderId.DROPBOX, parts = listOf(FILE_ID, RLKEY)), provider.createSharedLink(SHARE_ID))
    }

    @Test
    fun `an account that may not share is refused`() = runTest {
        val provider = dropboxProvider {
            respondJson("""{"error_summary":"settings_error/not_authorized/..","error":{}}""", HttpStatusCode.Conflict)
        }
        assertEquals(SharedLinkResult.Refused, provider.createSharedLink(SHARE_ID))
    }

    @Test
    fun `a public link of an unknown form is refused`() = runTest {
        val provider = dropboxProvider {
            respondJson("""{"url":"https://www.dropbox.com/s/abc/x.zip?dl=0","link_permissions":{"resolved_visibility":{".tag":"public"}}}""")
        }
        assertEquals(SharedLinkResult.Refused, provider.createSharedLink(SHARE_ID))
    }

    @Test
    fun `deleting a copy that is already gone is success`() = runTest {
        var path: JsonElement? = null
        val provider = dropboxProvider { request ->
            path = Json.parseToJsonElement((request.body as TextContent).text).field("path")
            respondJson("""{"error_summary":"path_lookup/not_found/..","error":{}}""", HttpStatusCode.Conflict)
        }
        provider.deleteSharedFile(SHARE_ID)
        assertEquals(JsonPrimitive("/shared/$SHARE_ID.zip"), path)
    }

    @Test
    fun `a deletion refused for another reason fails`() = runTest {
        val provider = dropboxProvider {
            respondJson("""{"error_summary":"path_write/disallowed_name/..","error":{}}""", HttpStatusCode.Conflict)
        }
        assertFailsWith<DropboxApiException> { provider.deleteSharedFile(SHARE_ID) }
    }

    /** Neither the library's pass nor its deletion guard may ever see a shared copy as a song. */
    @Test
    fun `a listing reports the shared copies apart from the library files`() = runTest {
        val provider = dropboxProvider {
            respondJson(
                """{"entries":[
                    {".tag":"folder","name":"shared","path_lower":"/shared"},
                    {".tag":"file","name":"$SHARE_ID.zip","path_lower":"/shared/$SHARE_ID.zip","rev":"r1","size":10,"content_hash":"h1"},
                    {".tag":"file","name":"notes.txt","path_lower":"/shared/notes.txt","rev":"r2"},
                    {".tag":"file","name":"deep.zip","path_lower":"/shared/x/deep.zip","rev":"r3"},
                    {".tag":"file","name":"song.cho","path_lower":"/songs/song.cho","rev":"r4","size":5},
                    {".tag":"file","name":"shared-links.json","path_lower":"/shared-links.json","rev":"r5"}
                ],"cursor":"c","has_more":false}""",
            )
        }

        val listing = provider.list()

        assertEquals(listOf(RemoteSharedFile(shareId = SHARE_ID, revision = "r1", contentHash = "h1", size = 10)), listing.sharedFiles)
        assertEquals(listOf(LibraryFileKind.SONG), listing.files.map { it.kind })
        assertEquals(mapOf("shared-links.json" to "r5"), listing.documentRevisions)
    }

    /** A rebuilt copy is this device's own write and says nothing another device needs to act on. */
    @Test
    fun `the change feed never reports a shared copy`() = runTest {
        val provider = dropboxProvider {
            respondJson(
                """{"entries":[
                    {".tag":"file","name":"$SHARE_ID.zip","path_lower":"/shared/$SHARE_ID.zip","rev":"r1"},
                    {".tag":"deleted","name":"old.zip","path_lower":"/shared/old.zip"}
                ],"cursor":"next","has_more":false}""",
            )
        }

        val changes = provider.changesSince("cursor")

        assertTrue(changes.files.isEmpty() && changes.deletedFiles.isEmpty() && changes.documents.isEmpty())
    }

    private fun MockRequestHandleScope.respondLink(visibility: String) = respondJson(linkJson(visibility))

    private fun linkJson(visibility: String) =
        """{".tag":"file","url":"$LINK_URL","name":"$SHARE_ID.zip","link_permissions":{"resolved_visibility":{".tag":"$visibility"}}}"""

    private fun JsonElement.field(name: String) = (this as JsonObject)[name]

    private companion object {
        const val SHARE_ID = "k3jq2a7bcdefghij"
        const val FILE_ID = "abcdefghij0123456789k"
        const val RLKEY = "abcdefghijklmnopqrstu1234"
        const val LINK_URL = "https://www.dropbox.com/scl/fi/$FILE_ID/$SHARE_ID.zip?rlkey=$RLKEY&dl=0"
    }
}
