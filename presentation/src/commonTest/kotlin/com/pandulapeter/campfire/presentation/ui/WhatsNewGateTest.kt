/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsNewGateTest {

    @Test
    fun opensWhenNothingIsInTheWay() {
        assertTrue(canShowWhatsNew(hasDialog = false, isImporting = false, hasImportReport = false, queuedImportCount = 0))
    }

    @Test
    fun waitsForAnotherDialog() {
        assertFalse(canShowWhatsNew(hasDialog = true, isImporting = false, hasImportReport = false, queuedImportCount = 0))
    }

    @Test
    fun waitsForAnImportRunning() {
        assertFalse(canShowWhatsNew(hasDialog = false, isImporting = true, hasImportReport = false, queuedImportCount = 0))
    }

    @Test
    fun waitsForAConflictsQuestion() {
        assertFalse(canShowWhatsNew(hasDialog = false, isImporting = false, hasImportReport = true, queuedImportCount = 0))
    }

    @Test
    fun waitsForABatchStillInTheQueue() {
        assertFalse(canShowWhatsNew(hasDialog = false, isImporting = false, hasImportReport = false, queuedImportCount = 1))
    }
}
