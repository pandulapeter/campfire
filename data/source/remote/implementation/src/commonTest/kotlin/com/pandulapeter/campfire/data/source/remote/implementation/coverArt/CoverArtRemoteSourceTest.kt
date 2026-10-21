/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.coverArt

import com.pandulapeter.campfire.data.source.remote.api.CoverArtDownload
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.configureClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CoverArtRemoteSourceTest {

    @Test
    fun `an image is downloaded`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val download = source { _ -> respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg")) }.downloadCoverArt(URL)

        assertContentEquals(bytes, assertIs<CoverArtDownload.Image>(download).bytes)
    }

    @Test
    fun `an answer that is not an image is missing`() = runTest {
        val download = source { _ -> respond("<html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html")) }.downloadCoverArt(URL)

        assertEquals(CoverArtDownload.Missing, download)
    }

    @Test
    fun `an image larger than a cover has any reason to be is missing`() = runTest {
        val bytes = ByteArray(CoverArtRemoteSourceImpl.MAXIMUM_SIZE_BYTES + 1)
        val download = source { _ -> respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png")) }.downloadCoverArt(URL)

        assertEquals(CoverArtDownload.Missing, download)
    }

    @Test
    fun `a cover cut short of its declared length is unreachable`() = runTest {
        val download = source { _ -> respond(ByteArray(500), HttpStatusCode.OK, imageHeaders(contentLength = 1000)) }.downloadCoverArt(URL)

        assertEquals(CoverArtDownload.Unreachable, download)
    }

    @Test
    fun `a cover served encoded is kept although its length was the encoded one`() = runTest {
        val encoded = source { _ -> respond(ByteArray(1000), HttpStatusCode.OK, imageHeaders(contentLength = 900, contentEncoding = "gzip")) }
        val encodingHidden = source { _ -> respond(ByteArray(1000), HttpStatusCode.OK, imageHeaders(contentLength = 1010)) }

        assertIs<CoverArtDownload.Image>(encoded.downloadCoverArt(URL))
        assertIs<CoverArtDownload.Image>(encodingHidden.downloadCoverArt(URL))
    }

    @Test
    fun `a cover of exactly its declared length is kept`() = runTest {
        val download = source { _ -> respond(ByteArray(1000), HttpStatusCode.OK, imageHeaders(contentLength = 1000)) }.downloadCoverArt(URL)

        assertEquals(1000, assertIs<CoverArtDownload.Image>(download).bytes.size)
    }

    @Test
    fun `nothing at the address is missing, and the service's own trouble is unreachable`() = runTest {
        assertEquals(CoverArtDownload.Missing, source { _ -> respondError(HttpStatusCode.NotFound) }.downloadCoverArt(URL))
        assertEquals(CoverArtDownload.Unreachable, source { _ -> respondError(HttpStatusCode.BadGateway) }.downloadCoverArt(URL))
    }

    @Test
    fun `a cover that never arrives is unreachable after a minute`() = runTest {
        val download = source { _ -> awaitCancellation() }.downloadCoverArt(URL)

        assertEquals(CoverArtDownload.Unreachable, download)
        assertTrue(currentTime in 60_000L..<10 * 60_000L)
    }

    @Test
    fun `a transport failure is unreachable`() = runTest {
        assertEquals(CoverArtDownload.Unreachable, source { _ -> throw IllegalStateException("No route to host") }.downloadCoverArt(URL))
    }

    @Test
    fun `an address on the local network is not asked`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = source { request ->
            requests += request
            respond(byteArrayOf(1), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
        }

        assertEquals(CoverArtDownload.Missing, source.downloadCoverArt("http://192.168.1.1/cover.jpg"))
        assertEquals(CoverArtDownload.Missing, source.downloadCoverArt("https://127.0.0.1:8443/cover.jpg"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a redirect into the local network is not followed`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val download = source { request ->
            requests += request
            if (requests.size == 1) {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://192.168.1.1/cover.jpg"))
            } else {
                respond(byteArrayOf(1), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
            }
        }.downloadCoverArt(URL)

        assertEquals(CoverArtDownload.Missing, download)
        assertEquals(1, requests.size)
    }

    /** Built on the test's scheduler, engine included, so that a request timeout is measured in virtual time. */
    private fun TestScope.source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = CoverArtRemoteSourceImpl(
        httpClientHolder = HttpClientHolder(dispatcher = StandardTestDispatcher(testScheduler)) {
            HttpClient(MockEngine.create {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            }) { configureClient(sendsUserAgent = true, hasSocketTimeout = true) }
        },
    )

    private fun imageHeaders(contentLength: Int, contentEncoding: String? = null) = Headers.build {
        append(HttpHeaders.ContentType, "image/jpeg")
        append(HttpHeaders.ContentLength, contentLength.toString())
        if (contentEncoding != null) append(HttpHeaders.ContentEncoding, contentEncoding)
    }

    private companion object {
        const val URL = "https://coverartarchive.org/release-group/abc/front-250"
    }
}
