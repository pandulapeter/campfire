/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SvgXmlTest {

    @Test
    fun `elements, attributes, and text are read with their entities resolved`() {
        val root = parseSvgXml("""<?xml version="1.0"?><!-- a comment --><svg a='1' b="x &amp; y"><text>&lt;&#x266F;&#9837;</text><g/></svg>""")!!
        assertEquals("svg", root.name)
        assertEquals(mapOf("a" to "1", "b" to "x & y"), root.attributes)
        assertEquals(listOf("text", "g"), root.elements.map { it.name })
        assertEquals("<♯♭", root.elements.first().text)
    }

    @Test
    fun `a character outside the basic plane is kept whole`() {
        assertEquals("𝄞", parseSvgXml("<t>&#x1D11E;</t>")!!.text)
    }

    @Test
    fun `a document that is not well-formed is not read`() {
        assertNull(parseSvgXml("<svg><g></svg>"))
        assertNull(parseSvgXml("<svg>"))
        assertNull(parseSvgXml("<svg/><svg/>"))
        assertNull(parseSvgXml("<svg a=1/>"))
        assertNull(parseSvgXml("<svg>&nbsp;</svg>"))
        assertNull(parseSvgXml("<svg><![CDATA[x]]></svg>"))
    }
}
