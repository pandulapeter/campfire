/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.page

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeLoadFailureTest {

    @Test
    fun `a load cancelled for another is no failure on any engine`() {
        assertEquals(NativeLoadFailure.NONE, NativeLoadFailure.of("NSURLErrorDomain", -999))
        assertEquals(NativeLoadFailure.NONE, NativeLoadFailure.of(NativeLoadFailure.WEB_VIEW2, 14))
        assertEquals(NativeLoadFailure.NONE, NativeLoadFailure.of("WebKitNetworkError", 302))
        assertEquals(NativeLoadFailure.NONE, NativeLoadFailure.of("g-io-error-quark", 19))
    }

    @Test
    fun `no connection and a host not found are the network's on macOS`() {
        assertEquals(NativeLoadFailure.UNREACHABLE, NativeLoadFailure.of("NSURLErrorDomain", -1009))
        assertEquals(NativeLoadFailure.UNREACHABLE, NativeLoadFailure.of("NSURLErrorDomain", -1003))
        assertEquals(NativeLoadFailure.UNREACHABLE, NativeLoadFailure.of("NSURLErrorDomain", -1001))
    }

    @Test
    fun `a host name not resolved and a timeout are the network's on Windows`() {
        assertEquals(NativeLoadFailure.UNREACHABLE, NativeLoadFailure.of(NativeLoadFailure.WEB_VIEW2, 13))
        assertEquals(NativeLoadFailure.UNREACHABLE, NativeLoadFailure.of(NativeLoadFailure.WEB_VIEW2, 7))
    }

    @Test
    fun `a failed lookup and a refused connection are the network's on Linux`() {
        assertEquals(NativeLoadFailure.UNREACHABLE, NativeLoadFailure.of("g-resolver-error-quark", 0))
        assertEquals(NativeLoadFailure.UNREACHABLE, NativeLoadFailure.of("g-io-error-quark", 39))
        assertEquals(NativeLoadFailure.UNREACHABLE, NativeLoadFailure.of("WebKitNetworkError", 300))
    }

    @Test
    fun `a certificate, a refused navigation, or a crash is the page's`() {
        assertEquals(NativeLoadFailure.FAILED, NativeLoadFailure.of("NSURLErrorDomain", -1202))
        assertEquals(NativeLoadFailure.FAILED, NativeLoadFailure.of("WebKitErrorDomain", 102))
        assertEquals(NativeLoadFailure.FAILED, NativeLoadFailure.of(NativeLoadFailure.WEB_VIEW2, 5))
        assertEquals(NativeLoadFailure.FAILED, NativeLoadFailure.of("WebKitPolicyError", 102))
        assertEquals(NativeLoadFailure.FAILED, NativeLoadFailure.of("WebContentProcess", 0))
    }

    @Test
    fun `a code is read only within its own domain`() {
        assertEquals(NativeLoadFailure.FAILED, NativeLoadFailure.of("g-tls-error-quark", 13))
    }
}
