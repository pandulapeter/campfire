/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songCard

import kotlin.test.Test
import kotlin.test.assertEquals

class SongCardNumberTest {

    @Test
    fun `a setlist's number column is as wide as its largest number`() {
        assertEquals(1, songCardNumberDigits(9))
        assertEquals(2, songCardNumberDigits(10))
        assertEquals(3, songCardNumberDigits(100))
    }

    @Test
    fun `an empty setlist still has a column of one digit`() {
        assertEquals(1, songCardNumberDigits(0))
    }
}
