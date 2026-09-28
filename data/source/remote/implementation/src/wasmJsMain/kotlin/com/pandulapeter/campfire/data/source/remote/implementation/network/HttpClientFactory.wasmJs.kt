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
import io.ktor.client.engine.js.Js

/**
 * The browser engine goes through `fetch`, so every request Campfire makes is subject to the origin's CORS rules.
 * Dropbox answers the calls used here with the headers that allows; a provider that does not would need a proxy,
 * which is exactly the sort of thing Campfire refuses to run. MusicBrainz and the Cover Art Archive send them too.
 *
 * No `User-Agent` of Campfire's own is sent from here, see [configureClient]. MusicBrainz documents no other way for
 * a page to name itself, so what it sees is the browser's own user agent and the page's `Origin`, which is the address
 * the web build is published at and says as much about who is asking.
 */
internal actual fun createHttpClient() = HttpClient(Js) { configureClient(sendsUserAgent = false) }
