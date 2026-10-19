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

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength

/**
 * Whether a body of [received] bytes is a transfer cut off before the length the answer declared. The declared length
 * is of the body as sent, so once the engine has decoded a compressed one (iOS and the browser keep the encoded
 * length, and the browser hides a cross-origin `Content-Encoding`) the body may be a little shorter or much longer: a
 * visible encoding answers false, and only a body shorter by more than gzip's worst-case expansion of incompressible
 * data counts, which a body cut short nearly always is by far.
 */
internal fun HttpResponse.isCutShort(received: Int): Boolean {
    val declared = contentLength() ?: return false
    val encoding = headers[HttpHeaders.ContentEncoding]
    if (encoding != null && !encoding.trim().equals("identity", ignoreCase = true)) return false
    return received < declared - (declared / 512 + 64)
}
