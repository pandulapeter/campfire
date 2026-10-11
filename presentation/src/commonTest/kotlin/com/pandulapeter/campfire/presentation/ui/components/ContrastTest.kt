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

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContrastTest {

    @Test
    fun `black and white trade places where they contrast with the background equally`() {
        assertFalse(isDarkerMoreLegible(Color(0xFF707070)))
        assertTrue(isDarkerMoreLegible(Color(0xFF7C7C7C)))
    }

    @Test
    fun `an orange as dark as the eye would put white on takes a black check`() = assertTrue(isDarkerMoreLegible(Color(0xFFF57C00)))
}
