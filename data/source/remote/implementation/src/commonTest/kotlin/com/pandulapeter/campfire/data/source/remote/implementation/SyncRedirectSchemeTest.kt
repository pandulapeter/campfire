/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncRedirectSchemeTest {

    private val debug = SyncRedirectScheme("campfire-debug")

    @Test
    fun `the debug build is called back on a scheme of its own`() =
        assertEquals(expected = "campfire-debug://oauth", actual = debug.redirect.uri)

    @Test
    fun `the debug build takes its own redirect`() =
        assertTrue(debug.redirect.isRedirect("campfire-debug://oauth?code=abc123&state=deadbeef"))

    @Test
    fun `the debug build does not take the store build's redirect`() =
        assertFalse(debug.redirect.isRedirect("campfire://oauth?code=abc123&state=deadbeef"))

    @Test
    fun `the store build does not take the debug build's redirect`() =
        assertFalse(SyncRedirectScheme.Default.redirect.isRedirect("campfire-debug://oauth?code=abc123&state=deadbeef"))

    @Test
    fun `the store build keeps the redirect registered for every platform`() =
        assertEquals(expected = "campfire://oauth", actual = SyncRedirectScheme.Default.redirect.uri)
}
