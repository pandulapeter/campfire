/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadOnlyInEffectTest {

    @Test
    fun `read only mode switched on is in effect`() = assertTrue(testPreferences().copy(isPerformanceModeEnabled = true).isReadOnlyInEffect)

    @Test
    fun `read only mode switched off is not`() = assertFalse(testPreferences().isReadOnlyInEffect)

    @Test
    fun `read only mode is not in effect with the songs off`() =
        assertFalse(testPreferences().copy(isPerformanceModeEnabled = true, areSongsEnabled = false).isReadOnlyInEffect)

    @Test
    fun `nothing read yet is not read only`() = assertFalse((null as UserPreferences?).isReadOnlyInEffect)
}
