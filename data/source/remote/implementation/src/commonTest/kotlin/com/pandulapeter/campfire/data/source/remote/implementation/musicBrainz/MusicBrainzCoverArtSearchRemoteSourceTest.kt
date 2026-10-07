/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.musicBrainz

import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchException
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.USER_AGENT
import com.pandulapeter.campfire.data.source.remote.implementation.network.userAgent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

/**
 * How the cover search keeps to MusicBrainz's pace. The service is a [MockEngine] and the waiting happens in virtual
 * time, so the spacing and the back-off are measured exactly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MusicBrainzCoverArtSearchRemoteSourceTest {

    @Test
    fun `the user agent names the app, its version and where to reach its maintainers`() {
        assertEquals("Campfire/4.5.0 ( https://github.com/pandulapeter/campfire )", userAgent("4.5.0"))
    }

    @Test
    fun `every request carries the user agent`() = runTest {
        val agents = mutableListOf<String?>()
        val source = source { request ->
            agents += request.headers[HttpHeaders.UserAgent]
            respondJson(EMPTY_RESULT)
        }

        source.searchCoverArt(QUERY, onBusy = {})

        assertEquals(listOf<String?>(USER_AGENT), agents)
    }

    @Test
    fun `requests are spaced by the interval however many searches ask at once`() = runTest {
        val starts = mutableListOf<Long>()
        val source = source { _ ->
            starts += currentTime
            respondJson(EMPTY_RESULT)
        }

        (1..3).map { async { source.searchCoverArt(QUERY, onBusy = {}) } }.awaitAll()

        assertEquals(listOf(0L, 1_100L, 2_200L), starts)
    }

    @Test
    fun `a request made after a pause is not held back`() = runTest {
        val starts = mutableListOf<Long>()
        val source = source { _ ->
            starts += currentTime
            respondJson(EMPTY_RESULT)
        }

        source.searchCoverArt(QUERY, onBusy = {})
        testScheduler.advanceTimeBy(5_000)
        source.searchCoverArt(QUERY, onBusy = {})

        assertEquals(listOf(0L, 5_000L), starts)
    }

    @Test
    fun `a refusal is waited out for as long as it asks, and reported as busy`() = runTest {
        val starts = mutableListOf<Long>()
        var busyCount = 0
        val source = source { _ ->
            starts += currentTime
            if (starts.size == 1) {
                respond("""{"error":"busy"}""", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.RetryAfter, "3"))
            } else {
                respondJson(EMPTY_RESULT)
            }
        }

        source.searchCoverArt(QUERY, onBusy = { busyCount++ })

        assertEquals(listOf(0L, 3_000L), starts)
        assertEquals(1, busyCount)
    }

    @Test
    fun `a refusal that says nothing is waited out for longer each time, and then given up on`() = runTest {
        val starts = mutableListOf<Long>()
        val source = source { _ ->
            starts += currentTime
            respond("", HttpStatusCode.ServiceUnavailable)
        }

        assertFailsWith<CoverArtSearchException> { source.searchCoverArt(QUERY, onBusy = {}) }

        assertEquals(listOf(0L, 2_000L, 6_000L, 14_000L, 30_000L, 62_000L), starts)
    }

    @Test
    fun `any other failure ends the search`() = runTest {
        val source = source { _ -> respond("", HttpStatusCode.BadRequest) }

        assertFailsWith<CoverArtSearchException> { source.searchCoverArt(QUERY, onBusy = {}) }
    }

    @Test
    fun `an answer that is not a search result ends the search`() = runTest {
        val source = source { _ -> respondJson("<html>") }

        assertFailsWith<CoverArtSearchException> { source.searchCoverArt(QUERY, onBusy = {}) }
    }

    @Test
    fun `a query with nothing to search by asks nothing`() = runTest {
        var requestCount = 0
        val source = source { _ ->
            requestCount++
            respondJson(EMPTY_RESULT)
        }

        assertEquals(emptyList(), source.searchCoverArt(CoverArtQuery(artist = "Queen", album = "", title = ""), onBusy = {}))
        assertEquals(0, requestCount)
    }

    private fun TestScope.source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = MusicBrainzCoverArtSearchRemoteSource(
        httpClientHolder = HttpClientHolder(dispatcher = StandardTestDispatcher(testScheduler)) {
            HttpClient(MockEngine.create {
                // The handler records virtual time, so it must run on that clock's scheduler too. An I/O thread can
                // reach it only after runTest has already advanced to the next request's turn.
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            }) {
                expectSuccess = false
                install(UserAgent) { agent = USER_AGENT }
            }
        },
        rateLimiter = MusicBrainzRateLimiter(timeSource = testScheduler.timeSource, interval = 1_100.milliseconds),
    )

    private fun MockRequestHandleScope.respondJson(body: String) = respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private companion object {
        val QUERY = CoverArtQuery(artist = "Green Day", album = "Dookie", title = "")
        const val EMPTY_RESULT = """{"release-groups":[]}"""
    }
}
