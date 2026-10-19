/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryKeysTest {

    @Test
    fun `every spelling of the keys names the twelve notes from C`() {
        UserPreferences.Accidentals.entries.forEach { accidentals ->
            assertEquals((0 until 12).toList(), libraryKeys(accidentals).map { ChordProChords.parse(it)!!.root })
        }
    }
}
