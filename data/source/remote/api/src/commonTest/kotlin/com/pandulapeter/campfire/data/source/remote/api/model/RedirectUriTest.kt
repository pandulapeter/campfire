/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RedirectUriTest {

    @Test
    fun `reads the code and the state out of a redirect`() {
        val parameters = redirectParameters("campfire://oauth?code=abc123&state=deadbeef")
        assertEquals(expected = "abc123", actual = parameters["code"])
        assertEquals(expected = "deadbeef", actual = parameters["state"])
    }

    @Test
    fun `reads a loopback redirect, which carries a path as well`() {
        val parameters = redirectParameters("http://127.0.0.1:53682/?code=abc123&state=deadbeef")
        assertEquals(expected = "abc123", actual = parameters["code"])
    }

    @Test
    fun `reads a redirect back to a page of the app itself`() {
        val parameters = redirectParameters("https://songs.example.com/campfire/?code=abc123&state=deadbeef")
        assertEquals(expected = "abc123", actual = parameters["code"])
    }

    @Test
    fun `has no code when the user refused`() {
        val parameters = redirectParameters("campfire://oauth?error=access_denied&state=deadbeef")
        assertNull(parameters["code"])
        assertEquals(expected = "access_denied", actual = parameters["error"])
    }

    /** An error description is prose, so it arrives percent and plus encoded. */
    @Test
    fun `decodes an encoded parameter`() = assertEquals(
        expected = "The user chose not to,",
        actual = redirectParameters("campfire:,//oauth?error_description=The+user+chose+not+to%2C")["error_description"]
    )

    @Test
    fun `decodes an escape outside the ascii range`() = assertEquals(
        expected = "árvíztűrő",
        actual = redirectParameters("campfire:,//oauth?name=%C3%A1rv%C3%ADzt%C5%B1r%C5%91")["name"]
    )

    /** A percent sign that is not the start of an escape stays a percent sign rather than eating the next character. */
    @Test
    fun `survives a percent sign that is not an escape`() = assertEquals(
        expected = "100% sure",
        actual = redirectParameters("campfire:,//oauth?note=100%+sure")["note"]
    )

    @Test
    fun `has nothing when there is no query at all`() =
        assertEquals(expected = emptyMap(), actual = redirectParameters("campfire://oauth"))

    /** The fragment is not part of the query, and a service that adds one must not confuse the last parameter. */
    @Test
    fun `ignores a fragment`() = assertEquals(
        expected = "deadbeef",
        actual = redirectParameters("campfire:,//oauth?state=deadbeef#section")["state"]
    )
}
