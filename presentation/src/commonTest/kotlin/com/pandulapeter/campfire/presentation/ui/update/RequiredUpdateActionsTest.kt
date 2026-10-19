/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.update

import kotlin.test.Test
import kotlin.test.assertEquals

class RequiredUpdateActionsTest {

    @Test
    fun `offline the blocking screen shows both update and continue offline`() = assertEquals(
        listOf("Update" to true, "Continue offline" to true),
        requiredUpdateActions(
            updateLabel = "Update",
            continueOfflineLabel = "Continue offline",
            onUpdate = {},
            onContinueOffline = {},
        ).map { it.text to it.isVisible },
    )

    @Test
    fun `online the blocking screen shows update with continue offline hidden`() = assertEquals(
        listOf("Update" to true, "Continue offline" to false),
        requiredUpdateActions(
            updateLabel = "Update",
            continueOfflineLabel = "Continue offline",
            onUpdate = {},
            onContinueOffline = null,
        ).map { it.text to it.isVisible },
    )
}
