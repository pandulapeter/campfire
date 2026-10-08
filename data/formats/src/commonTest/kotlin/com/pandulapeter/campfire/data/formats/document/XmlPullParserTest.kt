/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class XmlPullParserTest {
    @Test
    fun `many cdata sections in one element parse in linear time`() {
        val xml = "<a>" + ("<![CDATA[" + "x".repeat(54) + "]]>").repeat(120_000) + "</a>"
        lateinit var root: XmlElement
        val elapsed = measureTime { root = parseXml(xml) }
        assertEquals(120_000 * 54, root.text.length)
        assertTrue(elapsed < 2.seconds, "Took $elapsed")
    }

    @Test
    fun `mixed content concatenates in document order`() {
        val root = parseXml("<a>x<b/>y<![CDATA[z]]></a>")
        assertEquals("xyz", root.text)
        assertEquals("", root.child("b")?.text)
    }

    @Test
    fun `XML reader supports entities cdata and alternate prefixes but refuses dtds and deep trees`() {
        val root = parseXml("<x:p xmlns:x='urn:test'><x:t><![CDATA[a<b]]>&amp;&#x151;&#337;&quot;&apos;</x:t></x:p>")
        assertEquals("a<b&\u0151\u0151\"'", root.child("t")?.text)
        assertFailsWith<IllegalStateException> { parseXml("<!DOCTYPE a [<!ENTITY e 'expanded'>]><a>&e;</a>") }
        assertFailsWith<IllegalArgumentException> { parseXml("<a>".repeat(65) + "</a>".repeat(65)) }
        assertFailsWith<IllegalArgumentException> { parseXml("<a><b></a>") }
        assertFailsWith<IllegalArgumentException> { parseXml("<a>&#x110000;</a>") }
        assertFailsWith<IllegalArgumentException> { parseXml("<a>&external;</a>") }
    }

    @Test
    fun `pathologically many events are still rejected`() {
        assertFailsWith<IllegalArgumentException> { parseXml("<r>" + "<a/>".repeat(350_000) + "</r>") }
    }
}
