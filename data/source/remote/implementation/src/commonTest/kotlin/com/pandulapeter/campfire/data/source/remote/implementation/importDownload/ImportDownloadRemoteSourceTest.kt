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
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImportDownloadRemoteSourceTest {

    @Test
    fun `a file is downloaded whatever type the host says it is`() = runTest {
        val bytes = byteArrayOf(0x50, 0x4b, 3, 4)
        val download = source { _ -> respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html")) }.download(URL, MAX)

        assertContentEquals(bytes, assertIs<ImportDownload.File>(download).bytes)
    }

    @Test
    fun `a length over the ceiling is too large before the body is read`() = runTest {
        val download = source { _ ->
            respond(ByteReadChannel(ByteArray(4)), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, (MAX + 1).toString()))
        }.download(URL, MAX)

        assertEquals(ImportDownload.TooLarge, download)
    }

    @Test
    fun `a body over the ceiling with no length is too large`() = runTest {
        val download = source { _ -> respond(ByteReadChannel(ByteArray(MAX.toInt() + 1)), HttpStatusCode.OK) }.download(URL, MAX)

        assertEquals(ImportDownload.TooLarge, download)
    }

    @Test
    fun `a body exactly at the ceiling is downloaded`() = runTest {
        val download = source { _ -> respond(ByteArray(MAX.toInt()), HttpStatusCode.OK) }.download(URL, MAX)

        assertEquals(MAX.toInt(), assertIs<ImportDownload.File>(download).bytes.size)
    }

    @Test
    fun `nothing at the address is missing, and the host's own trouble is unreachable`() = runTest {
        assertEquals(ImportDownload.Missing, source { _ -> respondError(HttpStatusCode.NotFound) }.download(URL, MAX))
        assertEquals(ImportDownload.Missing, source { _ -> respondError(HttpStatusCode.Forbidden) }.download(URL, MAX))
        assertEquals(ImportDownload.Missing, source { _ -> respond(ByteArray(0), HttpStatusCode.OK) }.download(URL, MAX))
        assertEquals(ImportDownload.Unreachable, source { _ -> respondError(HttpStatusCode.BadGateway) }.download(URL, MAX))
        assertEquals(ImportDownload.Unreachable, source { _ -> respondError(HttpStatusCode.TooManyRequests) }.download(URL, MAX))
        assertEquals(ImportDownload.Unreachable, source { _ -> respondError(HttpStatusCode.RequestTimeout) }.download(URL, MAX))
    }

    @Test
    fun `a transport failure is unreachable and an address that does not parse is missing`() = runTest {
        assertEquals(ImportDownload.Unreachable, source { _ -> throw IllegalStateException("No route to host") }.download(URL, MAX))
        assertEquals(ImportDownload.Missing, source { _ -> respond(byteArrayOf(1), HttpStatusCode.OK) }.download("not an address", MAX))
    }

    @Test
    fun `the name comes from the header the host sends`() = runTest {
        val download = source { _ ->
            respond(byteArrayOf(1), HttpStatusCode.OK, headersOf(HttpHeaders.ContentDisposition, "attachment; filename=\"Gig.zip\""))
        }.download(URL, MAX)

        assertEquals("Gig.zip", assertIs<ImportDownload.File>(download).name)
    }

    @Test
    fun `the extended name is preferred and decoded`() {
        assertEquals(
            "Tükörfúrógép.cho",
            importDownloadFileName("attachment; filename=\"fallback.cho\"; filename*=UTF-8''T%C3%BCk%C3%B6rf%C3%BAr%C3%B3g%C3%A9p.cho", URL),
        )
    }

    @Test
    fun `a plain name is read quoted or bare`() {
        assertEquals("a b.zip", importDownloadFileName("attachment; filename=\"a b.zip\"", URL))
        assertEquals("a.zip", importDownloadFileName("inline;filename=a.zip; size=10", URL))
    }

    @Test
    fun `without a header the name is the last segment of the path, and without either it is a fallback`() {
        assertEquals("summer set.setlist.json", importDownloadFileName(null, "https://example.com/s/abc/summer%20set.setlist.json?dl=1"))
        assertEquals("download", importDownloadFileName(null, "https://example.com/"))
        assertEquals("download", importDownloadFileName("attachment", "https://example.com"))
    }

    @Test
    fun `a name is never a path`() {
        assertEquals("evil.cho", importDownloadFileName("attachment; filename=\"../../evil.cho\"", URL))
        assertEquals("evil.cho", importDownloadFileName("attachment; filename=\"..\\\\evil.cho\"", URL))
    }

    private fun source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = ImportDownloadRemoteSourceImpl(
        httpClientHolder = HttpClientHolder { HttpClient(MockEngine(handler)) { expectSuccess = false } },
    )

    private companion object {
        const val URL = "https://www.dropbox.com/scl/fi/abc/campfire_library?dl=1"
        const val MAX = 1024L
    }
}
