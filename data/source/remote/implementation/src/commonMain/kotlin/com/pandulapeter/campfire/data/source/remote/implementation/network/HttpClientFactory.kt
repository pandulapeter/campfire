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

/**
 * Every target picks the engine that is native to it: OkHttp on Android, CIO on the desktop, `NSURLSession` on iOS
 * and `fetch` in the browser. Nothing above this knows which, and the shared code stays free of JVM types.
 */
internal expect fun createHttpClient(): HttpClient
