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

import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportReportTest {

    @Test
    fun `every stage before the result is an import the process would lose`() {
        assertTrue(ImportReport.Loading(sourceName = "songs.zip").isUnfinished)
        assertTrue(ImportReport.Failed(reason = ImportFailure.UNREADABLE, sourceName = null).isUnfinished)
        assertTrue(ImportReport.Review(plan = ImportPlan(), choices = ImportChoices(), isDemoLibrary = false).isUnfinished)
        assertTrue(ImportReport.Importing.isUnfinished)
    }

    @Test
    fun `a result or no report at all loses nothing with the process`() {
        assertFalse(ImportReport.Finished(ImportResult(importedSongFileNames = listOf("a.cho"))).isUnfinished)
        assertFalse(ImportReport.Finished(null).isUnfinished)
        assertFalse((null as ImportReport?).isUnfinished)
    }
}
