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
import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteDeletion
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How the provider answers what Dropbox says about pace. The service is a [MockEngine] and the waiting happens in
 * virtual time, so a test of five retries takes no longer than a test of none.
 */
class DropboxRequestTest {

    /** Dropbox's answer to writes landing in one folder at once, which the engine's own concurrency provokes. */
    @Test
    fun `retries a write that was refused for too many write operations`() = runTest {
        val requests = mutableListOf<String>()
        val provider = dropboxProvider { request ->
            requests += request.url.toString()
            if (requests.size <= 3) {
                respondJson("""{"error_summary":"path/too_many_write_operations/...","error":{}}""", HttpStatusCode.Conflict)
            } else {
                respondJson("""{"name":"song.cho","rev":"0123456789abcdef"}""")
            }
        }
        assertEquals(
            expected = RemoteWriteResult.Written("0123456789abcdef"),
            actual = provider.upload(LibraryFileKind.SONG, "song.cho", byteArrayOf(1, 2, 3), expectedRevision = null),
        )
        assertEquals(expected = 4, actual = requests.size)
    }

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
        val deletions = (1..4).map { RemoteDeletion(LibraryFileKind.SONG, "song_$it.cho", expectedRevision = "r$it") }

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
        val bodies = mutableListOf<String>()
        val provider = dropboxProvider { request ->
            bodies += (request.body as TextContent).text
            if (bodies.size == 1) {
                respondJson(
                    """{".tag":"complete","entries":[
                        {".tag":"success","metadata":{}},
                        {".tag":"failure","failure":{".tag":"too_many_write_operations"}}
                    ]}""",
                )
            } else {
                respondJson("""{".tag":"failed","failed":{".tag":"too_many_write_operations"}}""").takeIf { bodies.size == 2 }
                    ?: respondJson("""{".tag":"complete","entries":[{".tag":"success","metadata":{}}]}""")
            }
        }
        val deletions = (1..2).map { RemoteDeletion(LibraryFileKind.SONG, "song_$it.cho", expectedRevision = null) }

        assertEquals(expected = emptyMap(), actual = provider.delete(deletions))
        assertEquals(expected = 3, actual = bodies.size)
        assertTrue(bodies.drop(1).all { "song_2.cho" in it && "song_1.cho" !in it })
    }

    @Test
    fun `waits as long as the body of a rate limited answer asks`() = runTest {
        var requestCount = 0
        val provider = dropboxProvider {
            requestCount++
            if (requestCount == 1) {
                respondJson(
                    """{"error_summary":"too_many_requests/...","error":{"reason":{".tag":"too_many_requests"},"retry_after":10}}""",
                    HttpStatusCode.TooManyRequests,
                )
            } else {
                respondJson(EMPTY_LISTING)
            }
        }
        provider.list()
        assertTrue(currentTime >= 10_000L, "Waited $currentTime ms rather than the 10 s that were asked for.")
    }

    /** A service having trouble of its own says nothing about when it will be over, so the wait grows instead. */
    @Test
    fun `waits longer every time the service is unavailable without saying for how long`() = runTest {
        var requestCount = 0
        val provider = dropboxProvider {
            requestCount++
            if (requestCount <= 6) {
                respond(content = "", status = HttpStatusCode.ServiceUnavailable)
            } else {
                respondJson(EMPTY_LISTING)
            }
        }
        provider.list()
        // 2 + 4 + 8 + 16 + 32 + 32 seconds, and less than a second of jitter on each.
        assertTrue(currentTime in 94_000L..<97_000L, "Waited $currentTime ms rather than about 94 s.")
    }

    @Test
    fun `gives up on a service that stays unavailable`() = runTest {
        val provider = dropboxProvider { respond(content = "", status = HttpStatusCode.ServiceUnavailable) }
        assertFailsWith<SyncNetworkException> { provider.list() }
    }

    @Test
    fun `an upload whose receipt cannot be read is one that may have landed`() = runTest {
        val provider = dropboxProvider { respond(content = "<html>", status = HttpStatusCode.OK) }
        assertFailsWith<SyncNetworkException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }
    }

    @Test
    fun `reports a full account as that rather than as one refused file`() = runTest {
        val provider = dropboxProvider { respondJson("""{"error_summary":"path/insufficient_space/..","error":{}}""", HttpStatusCode.Conflict) }
        assertFailsWith<SyncRemoteStorageFullException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }
    }

    @Test
    fun `reports a refused name as the refusal of that one file`() = runTest {
        val provider = dropboxProvider { respondJson("""{"error_summary":"path/malformed_path/..","error":{}}""", HttpStatusCode.Conflict) }
        assertFailsWith<DropboxApiException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }
    }

    /** A stopped run resumes every request in flight with a cancellation, and the engine has to be told that it is one. */
    @Test
    fun `a request that is cancelled stays a cancellation`() = runTest {
        val hasStarted = CompletableDeferred<Unit>()
        val provider = dropboxProvider {
            hasStarted.complete(Unit)
            awaitCancellation()
        }
        var failure: Throwable? = null
        val job = launch { failure = runCatching { provider.list() }.exceptionOrNull() }
        hasStarted.await()
        job.cancelAndJoin()
        assertIs<CancellationException>(failure)
    }

    /**
     * What the client does with its own request timeout: it cancels the call, and reports the reason instead. Retried
     * like any other timeout, and the service not being reached once the attempts run out.
     */
    @Test
    fun `a request that keeps timing out is the service not being reached`() = runTest {
        val provider = dropboxProvider(
            configure = { install(HttpTimeout) { requestTimeoutMillis = 50 } },
        ) { awaitCancellation() }
        val exception = assertFailsWith<SyncNetworkException> { provider.list() }
        assertIs<HttpRequestTimeoutException>(exception.cause)
    }

    @Test
    fun `a cancellation nobody asked for is the service not being reached`() = runTest {
        val provider = dropboxProvider { throw CancellationException("The engine gave up.") }
        assertFailsWith<SyncNetworkException> { provider.list() }
    }

    /** What the browser engine throws for a `fetch` that failed: a `kotlin.Error`, not an `Exception`. */
    @Test
    fun `a request the browser could not send is the service not being reached`() = runTest {
        val provider = dropboxProvider { throw Error("Fail to fetch") }
        assertFailsWith<SyncNetworkException> { provider.list() }
        assertFailsWith<SyncNetworkException> { provider.download(LibraryFileKind.SONG, "song.cho") }
        assertFailsWith<SyncNetworkException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }
    }

    @Test
    fun `disconnecting while the browser cannot send the revocation still disconnects`() = runTest {
        val storage = ConnectedStorage()
        val provider = dropboxProvider(storage = storage) { request ->
            if (request.url.toString() == REVOKE_URL) throw Error("Fail to fetch")
            error("No other request was expected.")
        }
        provider.disconnect()
        assertNull(storage.credentials)
    }

    @Test
    fun `the stored account is answered without a request`() = runTest {
        val provider = dropboxProvider(
            storage = ConnectedStorage(names = ""","displayName":"Jane","email":"jane@example.com""""),
        ) { error("No request was expected.") }
        assertEquals(
            expected = SyncAccount(SyncProviderId.DROPBOX, id = "", displayName = "Jane", email = "jane@example.com"),
            actual = provider.storedAccount(),
        )
    }

    @Test
    fun `a stored account whose name was never read goes by its id`() = runTest {
        val provider = dropboxProvider(storage = ConnectedStorage(names = ""","accountId":"dbid:1"""")) {
            error("No request was expected.")
        }
        assertEquals(
            expected = SyncAccount(SyncProviderId.DROPBOX, id = "dbid:1", displayName = "dbid:1", email = null),
            actual = provider.storedAccount(),
        )
    }

    @Test
    fun `a connection nothing was stored about has no stored account`() = runTest {
        val provider = dropboxProvider { error("No request was expected.") }
        assertNull(provider.storedAccount())
    }

    /** A cell handover or a tunnel, which the next attempt gets through. */
    @Test
    fun `a socket timeout is retried`() = runTest {
        var attempts = 0
        val provider = dropboxProvider {
            if (++attempts <= 2) throw SocketTimeoutException("Stalled")
            respondJson(EMPTY_LISTING)
        }
        provider.list()
        assertEquals(3, attempts)
    }

    @Test
    fun `a socket timeout on every attempt is the service not being reached`() = runTest {
        var attempts = 0
        val provider = dropboxProvider {
            attempts++
            throw SocketTimeoutException("Stalled")
        }
        assertIs<SocketTimeoutException>(assertFailsWith<SyncNetworkException> { provider.list() }.cause)
        assertEquals(7, attempts)
    }

    private companion object {
        const val REVOKE_URL = "https://api.dropboxapi.com/2/auth/token/revoke"
    }
}
