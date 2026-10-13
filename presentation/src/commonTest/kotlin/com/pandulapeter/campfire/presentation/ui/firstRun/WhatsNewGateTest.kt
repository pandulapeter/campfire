/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.firstRun

import kotlin.test.Test
import kotlin.test.assertEquals

class WhatsNewGateTest {

    @Test
    fun `What's new waits for every dialog and import that is in its way`() {
        val cases = mapOf(
            "nothing" to canShowWhatsNew(hasDialog = false, isImporting = false, hasImportReport = false, queuedImportCount = 0),
            "a dialog" to canShowWhatsNew(hasDialog = true, isImporting = false, hasImportReport = false, queuedImportCount = 0),
            "an import running" to canShowWhatsNew(hasDialog = false, isImporting = true, hasImportReport = false, queuedImportCount = 0),
            "an import under review" to canShowWhatsNew(hasDialog = false, isImporting = false, hasImportReport = true, queuedImportCount = 0),
            "a batch still queued" to canShowWhatsNew(hasDialog = false, isImporting = false, hasImportReport = false, queuedImportCount = 1),
        )
        assertEquals(mapOf("nothing" to true), cases.filterValues { it })
    }

    @Test
    fun `the onboarding stays off another dialog but not off an import, which follows it`() {
        assertEquals(true, canShowOnboarding(hasDialog = false))
        assertEquals(false, canShowOnboarding(hasDialog = true))
    }
}
