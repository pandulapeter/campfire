/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import kotlin.test.Test
import kotlin.test.assertEquals

internal class SetChordProTagUseCaseImplTest {

    private val setChordProTag = SetChordProTagUseCaseImpl()

    @Test
    fun aComposedTagRemovesItsDecomposedSpelling() {
        assertEquals("[C]a", setChordProTag(text = "{tag: Café}\n[C]a", tag = "Café", isSelected = false))
    }

    @Test
    fun aComposedTagIsNotAddedNextToItsDecomposedSpelling() {
        val text = "{tag: Café}\n[C]a"

        assertEquals(text, setChordProTag(text = text, tag = "Café", isSelected = true))
    }
}
