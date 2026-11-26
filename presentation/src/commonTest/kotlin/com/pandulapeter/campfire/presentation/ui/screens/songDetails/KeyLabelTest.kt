/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import kotlin.test.Test
import kotlin.test.assertEquals

internal class KeyLabelTest {

    @Test
    fun `words before the key lead it with a gap`() =
        assertEquals(KeyLabel(before = "Key of", isBeforeSpaced = true, after = "", isAfterSpaced = false), keyLabelOf("Key of %1\$s"))

    @Test
    fun `words after the key follow it with a gap`() =
        assertEquals(KeyLabel(before = "", isBeforeSpaced = false, after = "shapes", isAfterSpaced = true), keyLabelOf("%1\$s shapes"))

    @Test
    fun `a hyphenated compound stays joined to the key`() =
        assertEquals(KeyLabel(before = "", isBeforeSpaced = false, after = "-Griffe", isAfterSpaced = false), keyLabelOf("%1\$s-Griffe"))

    @Test
    fun `a template naming no key leads it`() =
        assertEquals(KeyLabel(before = "Key", isBeforeSpaced = true, after = "", isAfterSpaced = false), keyLabelOf("Key"))
}
