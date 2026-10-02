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

import com.pandulapeter.campfire.data.model.domain.ImportProgress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportProgressDialogTest {

    @Test
    fun theReadingPhasesCanBeCancelled() {
        assertTrue(ImportProgress.Phase.UNPACKING.isCancellable)
        assertTrue(ImportProgress.Phase.READING.isCancellable)
        assertTrue(ImportProgress.Phase.COMPARING.isCancellable)
    }

    @Test
    fun theWritingPhasesCannotBeCancelled() {
        assertFalse(ImportProgress.Phase.IMPORTING.isCancellable)
        assertFalse(ImportProgress.Phase.FINISHING.isCancellable)
    }
}
