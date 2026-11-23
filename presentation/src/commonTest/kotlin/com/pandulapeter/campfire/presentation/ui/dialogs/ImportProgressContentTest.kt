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

import com.pandulapeter.campfire.data.model.importing.ImportProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportProgressContentTest {

    @Test
    fun `only the phases before anything is written can be cancelled`() {
        ImportProgress.Phase.entries.forEach { phase ->
            // Exhaustive, so that a new phase has to be placed on one side of the line before this compiles.
            val writes = when (phase) {
                ImportProgress.Phase.RECEIVING,
                ImportProgress.Phase.DOWNLOADING,
                ImportProgress.Phase.READING_PAGE,
                ImportProgress.Phase.UNPACKING,
                ImportProgress.Phase.DOWNLOADING_PICTURES,
                ImportProgress.Phase.READING,
                ImportProgress.Phase.COMPARING,
                -> false
                ImportProgress.Phase.IMPORTING, ImportProgress.Phase.FINISHING -> true
            }
            assertEquals(!writes, phase.isCancellable, "$phase")
        }
    }

    @Test
    fun `a phase without bytes counts its files as it always has`() {
        val line = importProgressLineOf(ImportProgress(ImportProgress.Phase.UNPACKING, completed = 1, total = 4))
        assertEquals(ImportProgressLine.Files(completed = 1, total = 4), line)
        assertEquals(0.25f, line?.fraction)
    }

    @Test
    fun `a phase with neither bytes nor a file count says nothing under its line`() {
        assertNull(importProgressLineOf(ImportProgress(ImportProgress.Phase.DOWNLOADING)))
        assertNull(importProgressLineOf(ImportProgress(ImportProgress.Phase.READING, completed = 0, total = 0)))
    }

    @Test
    fun `an unpacking that counts bytes against a total says so and fills by them`() {
        val progress = ImportProgress(
            ImportProgress.Phase.UNPACKING,
            completed = 1,
            total = 2,
            completedBytes = 340_000_000,
            totalBytes = 1_200_000_000,
        )
        val line = importProgressLineOf(progress)
        assertEquals(
            ImportProgressLine.Bytes(ImportProgress.Phase.UNPACKING, completed = 340_000_000, total = 1_200_000_000, fraction = 340f / 1200f),
            line,
        )
    }

    @Test
    fun `a download of unknown size says how much has arrived and does not fill`() {
        val line = importProgressLineOf(ImportProgress(ImportProgress.Phase.DOWNLOADING, completedBytes = 5_000_000))
        assertEquals(ImportProgressLine.Bytes(ImportProgress.Phase.DOWNLOADING, completed = 5_000_000, total = null, fraction = null), line)
    }

    @Test
    fun `a copy counts its bytes never past the whole`() {
        val line = importProgressLineOf(ImportProgress(ImportProgress.Phase.RECEIVING, completedBytes = 12, totalBytes = 10))
        assertEquals(1f, line?.fraction)
    }

    @Test
    fun `bytes on a phase that does not move an archive are not shown`() {
        val line = importProgressLineOf(ImportProgress(ImportProgress.Phase.READING, completed = 2, total = 4, completedBytes = 10, totalBytes = 20))
        assertEquals(ImportProgressLine.Files(completed = 2, total = 4), line)
    }
}
