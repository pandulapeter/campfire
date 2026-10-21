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

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicAddressTest {

    @Test
    fun `a public https address is asked`() {
        listOf(
            "https://coverartarchive.org/release-group/a/front-250",
            "https://is1-ssl.mzstatic.com/image/thumb/x/100x100bb.jpg",
            "https://archive.org/x.jpg",
        ).forEach { address -> assertTrue(isPublicHttpsAddress(Url(address)), address) }
    }

    @Test
    fun `addresses on the device or the local network are not`() {
        listOf(
            "http://coverartarchive.org/x",
            "https://127.0.0.1/x",
            "https://192.168.1.1/x",
            "https://[::1]/x",
            "https://localhost:8443/x",
            "https://localhost./x",
            "https://nas/x",
            "https://printer.local/x",
            "https://0x7f.1/x",
            "https://2130706433/x",
        ).forEach { address -> assertFalse(isPublicHttpsAddress(Url(address)), address) }
    }
}
