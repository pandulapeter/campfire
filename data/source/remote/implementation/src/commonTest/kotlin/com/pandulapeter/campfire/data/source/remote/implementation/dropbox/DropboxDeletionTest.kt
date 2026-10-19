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
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteDeletion
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * How a run's deletions reach Dropbox: as `delete_batch` jobs, whose answers name each file's outcome. The service is a
 * [MockEngine] and the waiting happens in virtual time.
 */
class DropboxDeletionTest {

    /** A deletion made of single requests takes long enough for another device to see it half done. */
    @Test
    fun `deletes every file in one batch job and waits for it`() = runTest {
        val requests = mutableListOf<String>()
        val provider = dropboxProvider { request ->
            requests += request.url.encodedPath
            when (requests.size) {
                1 -> respondJson("""{".tag":"async_job_id","async_job_id":"job"}""")
                2 -> respondJson("""{".tag":"in_progress"}""")
                else -> respondJson(
                    """{".tag":"complete","entries":[
                        {".tag":"success","metadata":{}},
                        {".tag":"failure","failure":{".tag":"path_lookup","path_lookup":{".tag":"not_found"}}},
                        {".tag":"failure","failure":{".tag":"path_write","path_write":{".tag":"conflict","conflict":{".tag":"file"}}}},
                        {".tag":"failure","failure":{".tag":"path_lookup","path_lookup":{".tag":"restricted_content"}}}
                    ]}""",
                )
            }
        }
        val deletions = songDeletions(4)

        val failures = provider.delete(deletions)

        assertEquals(
            expected = listOf("/2/files/delete_batch", "/2/files/delete_batch/check", "/2/files/delete_batch/check"),
            actual = requests,
        )
        // Gone already and changed since both count as done; only the real refusal is reported.
        assertEquals(expected = mapOf(deletions[3] to "path_lookup/restricted_content"), actual = failures)
    }

    @Test
    fun `sends the files of a busy batch again`() = runTest {
        val sentPaths = mutableListOf<List<String>>()
        val provider = dropboxProvider { request ->
            sentPaths += pathsOf(request)
            when (sentPaths.size) {
                1 -> respondJson(
                    """{".tag":"complete","entries":[
                        {".tag":"success","metadata":{}},
                        {".tag":"failure","failure":{".tag":"too_many_write_operations"}}
                    ]}""",
                )

                2 -> respondJson("""{".tag":"failed","failed":{".tag":"too_many_write_operations"}}""")
                else -> respondJson("""{".tag":"complete","entries":[{".tag":"success","metadata":{}}]}""")
            }
        }

        assertEquals(expected = emptyMap(), actual = provider.delete(songDeletions(2)))
        assertEquals(
            expected = listOf(listOf("/songs/song_1.cho", "/songs/song_2.cho"), listOf("/songs/song_2.cho"), listOf("/songs/song_2.cho")),
            actual = sentPaths,
        )
    }

    @Test
    fun `more files than one job takes are deleted in several, every one of them once`() = runTest {
        val sentPaths = mutableListOf<List<String>>()
        val provider = dropboxProvider { request ->
            val paths = pathsOf(request)
            sentPaths += paths
            respondJson(paths.joinToString(prefix = """{".tag":"complete","entries":[""", postfix = "]}") { """{".tag":"success","metadata":{}}""" })
        }
        val deletions = songDeletions(DropboxSyncProvider.DELETE_BATCH_LIMIT + 1)

        assertEquals(expected = emptyMap(), actual = provider.delete(deletions))
        assertTrue(sentPaths.all { it.size <= DropboxSyncProvider.DELETE_BATCH_LIMIT }, "A job was sent ${sentPaths.maxOf { it.size }} files.")
        assertEquals(expected = deletions.map { "/songs/${it.name}" }, actual = sentPaths.flatten())
    }

    /** A page that is not Dropbox's comes from something in between, and says nothing about any one file. */
    @Test
    fun `a deletion answered with a page that is not Dropbox's is the network failing`() = runTest {
        val provider = dropboxProvider {
            respond(content = "<html>portal</html>", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "text/html"))
        }
        assertFailsWith<SyncNetworkException> { provider.delete(songDeletions(2)) }
    }

    @Test
    fun `a deletion answered with an empty object is the network failing`() = runTest {
        val provider = dropboxProvider { respondJson("{}") }
        assertFailsWith<SyncNetworkException> { provider.delete(songDeletions(2)) }
    }

    @Test
    fun `a file the answer says nothing about is reported as not deleted`() = runTest {
        val provider = dropboxProvider { respondJson("""{".tag":"complete","entries":[{".tag":"success","metadata":{}}]}""") }
        val deletions = songDeletions(2)

        assertEquals(expected = mapOf(deletions[1] to "no answer for this entry"), actual = provider.delete(deletions))
    }

    @Test
    fun `a job that failed for a reason other than a busy folder fails every file in it, without sending them again`() = runTest {
        var jobCount = 0
        val provider = dropboxProvider {
            jobCount++
            respondJson("""{".tag":"failed","failed":{".tag":"internal_error"}}""")
        }
        val deletions = songDeletions(2)

        assertEquals(expected = deletions.associateWith { "internal_error" }, actual = provider.delete(deletions))
        assertEquals(expected = 1, actual = jobCount)
    }

    private fun songDeletions(count: Int) = (1..count).map { RemoteDeletion(LibraryFileKind.SONG, "song_$it.cho", expectedRevision = null) }

    /** The files a `delete_batch` request names, read from its body the way Dropbox reads it. */
    private fun pathsOf(request: HttpRequestData) = Json.parseToJsonElement((request.body as TextContent).text)
        .jsonObject.getValue("entries").jsonArray.map { it.jsonObject.getValue("path").jsonPrimitive.content }
}
