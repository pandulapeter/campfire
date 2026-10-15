/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songFilters

import kotlin.test.Test
import kotlin.test.assertEquals

class FilterSearchTest {

    private val tags = listOf("christmas", "rock", "christian", "folk")

    private fun matching(query: String, selected: Set<String> = emptySet()) = chipsMatching(
        items = tags,
        normalizedQuery = query,
        isSelected = { it in selected },
        searchKeys = { listOf(it) },
    )

    @Test
    fun `an empty query leaves every chip`() {
        assertEquals(tags, matching(""))
    }

    @Test
    fun `a query leaves the chips containing it, in their order`() {
        assertEquals(listOf("christmas", "christian"), matching("christ"))
    }

    @Test
    fun `a selected chip stays whatever the query`() {
        assertEquals(listOf("rock", "folk"), matching("olk", selected = setOf("rock")))
    }

    @Test
    fun `any of a chip's keys can match`() {
        val languages = listOf("hu" to "hungarian", "en" to "english")
        val result = chipsMatching(
            items = languages,
            normalizedQuery = "hu",
            isSelected = { false },
            searchKeys = { listOf(it.first, it.second) },
        )
        assertEquals(listOf("hu" to "hungarian"), result)
    }
}
