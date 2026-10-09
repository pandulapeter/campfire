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

class LabeledControlRowTest {
    @Test
    fun `a label whose longest word fits beside the controls stays beside them`() {
        assertTrue(isLabelBesideControls(328, 100, 200, 16, stackedAtWidth = -1))
        assertTrue(isLabelBesideControls(316, 100, 200, 16, stackedAtWidth = -1))
    }

    @Test
    fun `a label whose longest word would be broken goes above the controls`() {
        assertFalse(isLabelBesideControls(315, 100, 200, 16, stackedAtWidth = -1))
    }

    @Test
    fun `controls wider than the row put the label above them`() {
        assertFalse(isLabelBesideControls(300, 0, 320, 16, stackedAtWidth = -1))
    }

    @Test
    fun `a row that stacked at a width stays stacked there although the controls narrowed`() {
        assertFalse(isLabelBesideControls(328, 100, 200, 16, stackedAtWidth = 328))
        assertTrue(isLabelBesideControls(400, 100, 200, 16, stackedAtWidth = 328))
    }
}
