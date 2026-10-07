/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class HttpClientHolderTest {

    @Test
    fun `callers asking at once share the one client, built once`() = runTest {
        var creationCount = 0
        val holder = HttpClientHolder {
            creationCount++
            HttpClient(MockEngine { respondOk() })
        }

        val clients = withContext(Dispatchers.Default) { List(8) { async { holder.client() } }.awaitAll() }

        assertEquals(1, creationCount)
        clients.forEach { assertSame(clients.first(), it) }
    }

    @Test
    fun `a holder nobody asks never builds a client`() {
        var creationCount = 0
        HttpClientHolder {
            creationCount++
            HttpClient(MockEngine { respondOk() })
        }

        assertEquals(0, creationCount)
    }
}
