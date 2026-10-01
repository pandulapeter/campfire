/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class TagOrderTest {

    @Test
    fun `labels are ordered alphabetically without regard to case or accents`() {
        assertEquals(
            listOf("acoustic", "Blues", "Ének", "Folk", "Őszi", "Rock"),
            listOf("Rock", "Őszi", "acoustic", "Folk", "Ének", "Blues").sortedAlphabeticallyBy { it },
        )
    }

    @Test
    fun `labels that differ only by case or accents keep a stable order`() {
        assertEquals(listOf("Eger", "eger", "éger"), listOf("éger", "eger", "Eger").sortedAlphabeticallyBy { it })
    }

    @Test
    fun `items are ordered by the label they are shown with`() {
        assertEquals(
            listOf("en", "de", "hu"),
            listOf("hu" to "Hungarian", "en" to "English", "de" to "German").sortedAlphabeticallyBy { it.second }.map { it.first },
        )
    }

    @Test
    fun `tags ordered by usage keep the order the library counted them in`() {
        val tags = listOf(Tag("rock", 5), Tag("Blues", 3), Tag("ács", 1))

        assertEquals(tags, tags.orderedBy(UserPreferences.LabelSortingMode.BY_USAGE))
        assertEquals(listOf("ács", "Blues", "rock"), tags.orderedBy(UserPreferences.LabelSortingMode.ALPHABETICAL).map { it.name })
    }

    @Test
    fun `languages ordered alphabetically keep the songs that declare none last`() {
        val languages = listOf(SongLanguage("hu", 4), SongLanguage(SongLanguage.UNKNOWN, 3), SongLanguage("en", 2), SongLanguage("de", 1))
        val names = mapOf("hu" to "Hungarian", "en" to "English", "de" to "German", SongLanguage.UNKNOWN to "Aaa")

        assertEquals(languages, languages.orderedBy(UserPreferences.LabelSortingMode.BY_USAGE) { names.getValue(it) })
        assertEquals(
            listOf("en", "de", "hu", SongLanguage.UNKNOWN),
            languages.orderedBy(UserPreferences.LabelSortingMode.ALPHABETICAL) { names.getValue(it) }.map { it.code },
        )
    }
}
