/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The redirect as another app, with a scheme and host of its own, would declare it: nothing of Campfire's is assumed. */
class OAuthRedirectConfigurationTest {

    private val redirect = OAuthRedirectConfiguration(scheme = "notes", host = "signin", desktopPort = 41234)

    @Test
    fun `the redirect uri is built from the configured scheme and host`() =
        assertEquals(expected = "notes://signin", actual = redirect.uri)

    @Test
    fun `an answer on the configured scheme and host is a redirect`() =
        assertTrue(redirect.isRedirect("notes://signin?code=abc123&state=deadbeef"))

    @Test
    fun `an answer on another app's redirect is not taken for this one's`() =
        assertFalse(redirect.isRedirect("campfire://oauth?code=abc123&state=deadbeef"))

    @Test
    fun `a file opened with the app is not a redirect`() =
        assertFalse(redirect.isRedirect("content://downloads/notes/signin.txt"))
}
