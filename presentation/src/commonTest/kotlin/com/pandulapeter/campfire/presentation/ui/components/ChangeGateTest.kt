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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangeGateTest {

    @Test
    fun `a value that never changes stays unchanged`() {
        val gate = ChangeGate(resting = 1)
        assertFalse(gate.hasChangedTo(1))
        assertFalse(gate.hasChangedTo(1))
    }

    @Test
    fun `a value that changes and comes back stays changed`() {
        val gate = ChangeGate(resting = false)
        assertTrue(gate.hasChangedTo(true))
        assertTrue(gate.hasChangedTo(false))
    }
}
