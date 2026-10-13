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
            "a conflicts question" to canShowWhatsNew(hasDialog = false, isImporting = false, hasImportReport = true, queuedImportCount = 0),
            "a batch still queued" to canShowWhatsNew(hasDialog = false, isImporting = false, hasImportReport = false, queuedImportCount = 1),
        )
        assertEquals(mapOf("nothing" to true), cases.filterValues { it })
    }

    @Test
    fun `the onboarding stays off another dialog and an import`() {
        val cases = mapOf(
            "nothing" to canShowOnboarding(hasDialog = false, hasImportReport = false, hasQueuedImport = false),
            "a dialog" to canShowOnboarding(hasDialog = true, hasImportReport = false, hasQueuedImport = false),
            "an import screen" to canShowOnboarding(hasDialog = false, hasImportReport = true, hasQueuedImport = false),
            "a file waiting to be imported" to canShowOnboarding(hasDialog = false, hasImportReport = false, hasQueuedImport = true),
        )
        assertEquals(mapOf("nothing" to true), cases.filterValues { it })
    }
}
