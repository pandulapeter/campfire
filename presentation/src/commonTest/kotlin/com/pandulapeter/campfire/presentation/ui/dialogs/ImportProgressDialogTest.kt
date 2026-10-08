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
import kotlin.test.assertEquals

class ImportProgressDialogTest {

    @Test
    fun `only the phases before anything is written can be cancelled`() {
        ImportProgress.Phase.entries.forEach { phase ->
            // Exhaustive, so that a new phase has to be placed on one side of the line before this compiles.
            val writes = when (phase) {
                ImportProgress.Phase.UNPACKING, ImportProgress.Phase.READING, ImportProgress.Phase.COMPARING -> false
                ImportProgress.Phase.IMPORTING, ImportProgress.Phase.FINISHING -> true
            }
            assertEquals(!writes, phase.isCancellable, "$phase")
        }
    }
}
