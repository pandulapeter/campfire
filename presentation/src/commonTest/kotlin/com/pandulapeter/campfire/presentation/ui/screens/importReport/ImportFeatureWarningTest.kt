/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportFeatureWarningTest {

    @Test
    fun `an import with the songs switched off is warned about, setlists or not`() {
        assertEquals(ImportFeatureWarning.SONGS_OFF, importFeatureWarning(areSongsEnabled = false, areSetlistsEnabled = true, hasSetlists = false))
        assertEquals(ImportFeatureWarning.SONGS_OFF, importFeatureWarning(areSongsEnabled = false, areSetlistsEnabled = false, hasSetlists = true))
    }

    @Test
    fun `setlists switched off are warned about only where the import brings some`() {
        assertEquals(ImportFeatureWarning.SETLISTS_OFF, importFeatureWarning(areSongsEnabled = true, areSetlistsEnabled = false, hasSetlists = true))
        assertNull(importFeatureWarning(areSongsEnabled = true, areSetlistsEnabled = false, hasSetlists = false))
        assertNull(importFeatureWarning(areSongsEnabled = true, areSetlistsEnabled = true, hasSetlists = true))
    }
}
