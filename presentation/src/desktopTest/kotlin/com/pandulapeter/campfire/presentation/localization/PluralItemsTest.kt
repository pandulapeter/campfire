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
import kotlin.test.assertTrue

/**
 * Only the items every table needs are checked, not every category a language's rule yields: a few Hebrew plurals have
 * no `two` on purpose, and no Spanish, Italian, or French one has `many`.
 */
class PluralItemsTest {

    @Test
    fun `every plural in every language has its one and other items`() {
        val tables = stringTables()
        assertTrue(tables.size >= 8)
        tables.forEach { (folder, plurals) ->
            plurals.forEach { (name, quantities) ->
                assertTrue("one" in quantities && "other" in quantities, "$folder/$name has $quantities")
            }
        }
    }

    @Test
    fun `every Romanian plural has its few item, for the counts worded without de`() {
        val plurals = stringTables().getValue("values-ro")
        assertTrue(plurals.isNotEmpty())
        plurals.forEach { (name, quantities) -> assertTrue("few" in quantities, "values-ro/$name has $quantities") }
    }

    /** Each `values*` folder's plurals, by name, with the quantities of their items. */
    private fun stringTables() = File("src/commonMain/composeResources").listFiles()!!
        .filter { it.name.startsWith("values") }
        .associate { folder ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(folder, "strings.xml"))
            val plurals = document.getElementsByTagName("plurals")
            folder.name to (0 until plurals.length).map { plurals.item(it) as Element }.associate { plural ->
                val items = plural.getElementsByTagName("item")
                plural.getAttribute("name") to (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet()
            }
        }
}
