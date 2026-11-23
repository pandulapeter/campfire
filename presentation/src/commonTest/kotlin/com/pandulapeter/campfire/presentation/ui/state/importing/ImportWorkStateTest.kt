/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.presentation.ui.dialogs.isCancellable
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportWorkStateTest {

    /** The work each phase is reported during: the preparation reads, compares, and downloads pictures while unpacking. */
    private val ImportProgress.Phase.workState
        get() = when (this) {
            ImportProgress.Phase.RECEIVING -> ImportWorkState.RECEIVING
            ImportProgress.Phase.DOWNLOADING, ImportProgress.Phase.READING_PAGE -> ImportWorkState.DOWNLOADING
            ImportProgress.Phase.UNPACKING,
            ImportProgress.Phase.DOWNLOADING_PICTURES,
            ImportProgress.Phase.READING,
            ImportProgress.Phase.COMPARING -> ImportWorkState.UNPACKING
            ImportProgress.Phase.IMPORTING, ImportProgress.Phase.FINISHING -> ImportWorkState.WRITING
        }

    @Test
    fun `the screen and the notification offer Cancel for the same phases`() = ImportProgress.Phase.entries.forEach { phase ->
        assertEquals(phase.workState.isCancellable, phase.isCancellable, phase.name)
    }
}
