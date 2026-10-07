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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's one HttpClient, built the first time a request needs it rather than when the dependency graph is: building
 * it loads a few hundred classes, which the view model's construction would otherwise pay for on the main thread in the
 * first composition, for users who never connect sync or search for a cover. Built on Dispatchers.Default for the same
 * reason, whoever asks first.
 */
internal class HttpClientHolder(create: () -> HttpClient) {

    private val client = lazy(create)

    suspend fun client(): HttpClient = if (client.isInitialized()) client.value else withContext(Dispatchers.Default) { client.value }
}
