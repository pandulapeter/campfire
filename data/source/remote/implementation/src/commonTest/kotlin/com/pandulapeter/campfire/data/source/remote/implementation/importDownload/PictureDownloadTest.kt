/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.importDownload

import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.source.remote.implementation.USER_AGENT
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.configureClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
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

class PictureDownloadTest {

    @Test
    fun `a picture is downloaded whatever type the host says it is`() = runTest {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)
        val download = source { _ -> respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/octet-stream")) }
            .downloadPicture(URL, MAX)

        assertContentEquals(bytes, assertIs<ImportDownload.File>(download).bytes)
    }

    @Test
    fun `a picture over the ceiling is too large - declared or not`() = runTest {
        val declared = source { _ ->
            respond(ByteReadChannel(ByteArray(4)), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, (MAX + 1).toString()))
        }.downloadPicture(URL, MAX)
        val undeclared = source { _ -> respond(ByteReadChannel(ByteArray(MAX.toInt() + 1)), HttpStatusCode.OK) }.downloadPicture(URL, MAX)

        assertEquals(ImportDownload.TooLarge, declared)
        assertEquals(ImportDownload.TooLarge, undeclared)
    }

    @Test
    fun `an empty picture is missing - even with a length declared`() = runTest {
        assertEquals(ImportDownload.Missing, source { _ -> respond(ByteArray(0), HttpStatusCode.OK) }.downloadPicture(URL, MAX))
        assertEquals(
            ImportDownload.Missing,
            source { _ -> respond(ByteArray(0), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "500")) }.downloadPicture(URL, MAX),
        )
    }

    @Test
    fun `a picture cut short of its declared length is unreachable`() = runTest {
        val download = source { _ -> respond(ByteArray(500), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "1000")) }.downloadPicture(URL, MAX)

        assertEquals(ImportDownload.Unreachable, download)
    }

    @Test
    fun `a picture is named by the address its redirects ended at`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val download = source { request ->
            requests += request
            if (requests.size == 1) {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://cdn.example.com/files/chart.png"))
            } else {
                respond(byteArrayOf(1), HttpStatusCode.OK)
            }
        }.downloadPicture("https://example.com/link/42", MAX)

        assertEquals("chart.png", assertIs<ImportDownload.File>(download).name)
    }

    @Test
    fun `a picture's name comes from the header the host sends`() = runTest {
        val download = source { _ ->
            respond(byteArrayOf(1), HttpStatusCode.OK, headersOf(HttpHeaders.ContentDisposition, "attachment; filename=\"Chart.png\""))
        }.downloadPicture(URL, MAX)

        assertEquals("Chart.png", assertIs<ImportDownload.File>(download).name)
    }

    @Test
    fun `nothing at the address is missing and the host's own trouble or a failed transfer is unreachable`() = runTest {
        assertEquals(ImportDownload.Missing, source { _ -> respondError(HttpStatusCode.NotFound) }.downloadPicture(URL, MAX))
        assertEquals(ImportDownload.Missing, source { _ -> respondError(HttpStatusCode.Forbidden) }.downloadPicture(URL, MAX))
        assertEquals(ImportDownload.Unreachable, source { _ -> respondError(HttpStatusCode.ServiceUnavailable) }.downloadPicture(URL, MAX))
        assertEquals(ImportDownload.Unreachable, source { _ -> respondError(HttpStatusCode.TooManyRequests) }.downloadPicture(URL, MAX))
        assertEquals(ImportDownload.Unreachable, source { _ -> respondError(HttpStatusCode.RequestTimeout) }.downloadPicture(URL, MAX))
        assertEquals(ImportDownload.Unreachable, source { _ -> throw IllegalStateException("No route to host") }.downloadPicture(URL, MAX))
    }

    @Test
    fun `a picture that never arrives is unreachable after two minutes`() = runTest {
        val download = source { _ -> awaitCancellation() }.downloadPicture(URL, MAX)

        assertEquals(ImportDownload.Unreachable, download)
        assertTrue(currentTime in 120_000L..<10 * 60_000L)
    }

    @Test
    fun `an address that is not public https is not asked`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = source { request ->
            requests += request
            respond(byteArrayOf(1), HttpStatusCode.OK)
        }

        assertEquals(ImportDownload.Missing, source.downloadPicture("http://example.com/chart.png", MAX))
        assertEquals(ImportDownload.Missing, source.downloadPicture("https://192.168.1.1/chart.png", MAX))
        assertEquals(ImportDownload.Missing, source.downloadPicture("https://nas.local/chart.png", MAX))
        assertEquals(ImportDownload.Missing, source.downloadPicture("chart.png", MAX))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a redirect into the local network is not followed`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val download = source { request ->
            requests += request
            if (requests.size == 1) {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://10.0.0.2/chart.png"))
            } else {
                respond(byteArrayOf(1), HttpStatusCode.OK)
            }
        }.downloadPicture(URL, MAX)

        assertEquals(ImportDownload.Missing, download)
        assertEquals(1, requests.size)
    }

    /** Built on the test's scheduler, engine included, so that the bounded time is measured in virtual time. */
    private fun TestScope.source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = ImportDownloadRemoteSourceImpl(
        httpClientHolder = HttpClientHolder(dispatcher = StandardTestDispatcher(testScheduler)) {
            HttpClient(MockEngine.create {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            }) { configureClient(userAgent = USER_AGENT, hasSocketTimeout = true) }
        },
    )

    private companion object {
        const val URL = "https://example.com/charts/wonderwall.png"
        const val MAX = 1024L
    }
}
