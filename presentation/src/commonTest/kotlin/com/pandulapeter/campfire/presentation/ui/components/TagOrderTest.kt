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
}
