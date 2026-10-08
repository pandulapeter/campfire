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

class NormalizeTextUseCaseImplTest {

    private val normalizeText = NormalizeTextUseCaseImpl()

    @Test
    fun `a decomposed accent sorts with the composed one`() {
        // What macOS and iOS hand out for a name typed with an accent, see normalizedToNfc.
        val decomposed = "Bésame mucho"

        assertEquals("besame mucho", normalizeText(decomposed))
        assertEquals(normalizeText("Bésame mucho"), normalizeText(decomposed))
    }
}
