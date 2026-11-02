/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.localization

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A key missing from a language compiles and silently shows the English text, and a key left only in a translation is
 * dead weight no build notices, so every language must name exactly the keys of the default `values` folder.
 */
class StringKeysTest {

    @Test
    fun `every language has exactly the keys of the default strings`() {
        val keys = File("src/commonMain/composeResources").listFiles()!!
            .filter { it.name.startsWith("values") }
            .associate { folder -> folder.name to keysOf(File(folder, "strings.xml")) }
        val default = keys.getValue("values")
        assertTrue(keys.size >= 8)
        keys.forEach { (folder, names) ->
            assertEquals(emptySet(), default - names, "missing from $folder")
            assertEquals(emptySet(), names - default, "only in $folder")
        }
    }

    private fun keysOf(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return listOf("string", "plurals").flatMap { tag ->
            val elements = document.getElementsByTagName(tag)
            (0 until elements.length).map { (elements.item(it) as Element).getAttribute("name") }
        }.toSet()
    }
}
