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

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonTest {

    @Test
    fun `quotes a plain string`() = assertEquals(
        expected = "\"/songs/Artist - Title.cho\"",
        actual = "/songs/Artist - Title.cho".toAsciiJsonString(),
    )

    /**
     * The reason this exists: the Dropbox upload and download calls take their arguments in an HTTP header, which
     * may only carry ASCII - and Campfire's song files are named after their titles.
     */
    @Test
    fun `escapes everything above ascii, so that a header can carry it`() = assertEquals(
        expected = "\"/songs/\\u00c1rv\\u00edzt\\u0171r\\u0151.cho\"",
        actual = "/songs/Árvíztűrő.cho".toAsciiJsonString(),
    )

    @Test
    fun `escapes quotes and backslashes`() = assertEquals(
        expected = "\"a\\\"b\\\\c\"",
        actual = "a\"b\\c".toAsciiJsonString(),
    )

    @Test
    fun `escapes control characters`() = assertEquals(
        expected = "\"a\\nb\\tc\"",
        actual = "a\nb\tc".toAsciiJsonString(),
    )

    @Test
    fun `leaves the unreserved characters of a query alone`() =
        assertEquals(expected = "abcXYZ019-_.~", actual = "abcXYZ019-_.~".urlEncode())

    @Test
    fun `encodes the characters that would otherwise end a query parameter`() =
        assertEquals(expected = "a%3Db%26c%3Fd", actual = "a=b&c?d".urlEncode())

    @Test
    fun `encodes a redirect uri so that it survives being a query parameter`() =
        assertEquals(expected = "http%3A%2F%2F127.0.0.1%3A53682", actual = "http://127.0.0.1:53682".urlEncode())

    @Test
    fun `encodes a character outside the ascii range as its utf-8 bytes`() =
        assertEquals(expected = "%C3%A1", actual = "á".urlEncode())

    @Test
    fun `encodes a space`() = assertEquals(expected = "a%20b", actual = "a b".urlEncode())
}
