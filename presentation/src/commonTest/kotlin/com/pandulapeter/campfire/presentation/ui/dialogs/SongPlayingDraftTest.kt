/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongPlayingDraftTest {

    @Test
    fun `empty values declare nothing and can always be saved`() {
        assertTrue(isValidSongPlayingDraft(capo = "", tempo = ""))
    }

    @Test
    fun `values inside their ranges can be saved, including both ends`() {
        assertTrue(isValidSongPlayingDraft(capo = "0", tempo = "30"))
        assertTrue(isValidSongPlayingDraft(capo = "12", tempo = "300"))
    }

    @Test
    fun `a value outside its range keeps the sheet from saving`() {
        assertFalse(isValidSongPlayingDraft(capo = "13", tempo = ""))
        assertFalse(isValidSongPlayingDraft(capo = "", tempo = "29"))
        assertFalse(isValidSongPlayingDraft(capo = "", tempo = "301"))
    }
}
