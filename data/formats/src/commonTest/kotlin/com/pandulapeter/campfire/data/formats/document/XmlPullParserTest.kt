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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class XmlPullParserTest {
    @Test
    fun manyCdataSectionsInOneElementParseInLinearTime() {
        val xml = "<a>" + ("<![CDATA[" + "x".repeat(54) + "]]>").repeat(120_000) + "</a>"
        lateinit var root: XmlElement
        val elapsed = measureTime { root = parseXml(xml) }
        assertEquals(120_000 * 54, root.text.length)
        assertTrue(elapsed < 2.seconds, "Took $elapsed")
    }

    @Test
    fun mixedContentConcatenatesInDocumentOrder() {
        val root = parseXml("<a>x<b/>y<![CDATA[z]]></a>")
        assertEquals("xyz", root.text)
        assertEquals("", root.child("b")?.text)
    }
}
