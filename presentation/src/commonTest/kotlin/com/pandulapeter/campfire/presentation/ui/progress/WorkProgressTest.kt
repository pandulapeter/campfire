/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.progress

import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.sync.SyncProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkProgressTest {

    @Test
    fun `nothing running is no work`() {
        assertNull(progress())
    }

    @Test
    fun `a sync run that only lists keeps a bar up without bringing one up`() {
        assertEquals(WorkProgress(fraction = null, isWorthShowing = false), progress(syncProgress = SyncProgress()))
    }

    @Test
    fun `a sync run moving files fills the bar`() {
        assertEquals(WorkProgress(fraction = 0.25f), progress(syncProgress = SyncProgress(completed = 1, total = 4)))
    }

    @Test
    fun `an import reading its files runs the line without a fill`() {
        assertEquals(
            WorkProgress(fraction = null),
            progress(isImporting = true, importProgress = ImportProgress(ImportProgress.Phase.READING, completed = 3, total = 4)),
        )
    }

    @Test
    fun `an import writing its files fills the bar`() {
        assertEquals(
            WorkProgress(fraction = 0.5f),
            progress(isImporting = true, importProgress = ImportProgress(ImportProgress.Phase.IMPORTING, completed = 2, total = 4)),
        )
    }

    @Test
    fun `an import moving an archive of known size fills the bar by its bytes`() {
        ImportProgress.Phase.entries.filter { it.countsBytes }.forEach { phase ->
            assertEquals(
                WorkProgress(fraction = 0.25f),
                progress(isImporting = true, importProgress = ImportProgress(phase, completedBytes = 250, totalBytes = 1_000)),
                "$phase",
            )
        }
    }

    @Test
    fun `an import moving bytes of unknown size runs the line without a fill`() {
        assertEquals(
            WorkProgress(fraction = null),
            progress(isImporting = true, importProgress = ImportProgress(ImportProgress.Phase.DOWNLOADING, completedBytes = 250)),
        )
    }

    @Test
    fun `an unpacking without bytes runs the line without a fill`() {
        assertEquals(
            WorkProgress(fraction = null),
            progress(isImporting = true, importProgress = ImportProgress(ImportProgress.Phase.UNPACKING, completed = 1, total = 4)),
        )
    }

    @Test
    fun `work the user started comes before a sync run`() {
        val syncProgress = SyncProgress(completed = 1, total = 4)
        assertEquals(WorkProgress(fraction = null), progress(isAddingToReview = true, syncProgress = syncProgress))
        assertEquals(WorkProgress(fraction = null), progress(isEditingSongs = true, syncProgress = syncProgress))
    }

    @Test
    fun `a picture being made to fit runs the line - before a sync run`() {
        assertEquals(WorkProgress(fraction = null), progress(isFittingPictures = true, syncProgress = SyncProgress(completed = 1, total = 4)))
    }

    @Test
    fun `the demo library planted on a first launch is no work`() {
        assertNull(progress(isImporting = true, isDemoLibraryPending = true))
    }

    private fun progress(
        isImporting: Boolean = false,
        isDemoLibraryPending: Boolean = false,
        importProgress: ImportProgress? = null,
        isAddingToReview: Boolean = false,
        isEditingSongs: Boolean = false,
        isFittingPictures: Boolean = false,
        syncProgress: SyncProgress? = null,
    ) = workProgress(
        isImporting = isImporting,
        isDemoLibraryPending = isDemoLibraryPending,
        importProgress = importProgress,
        isAddingToReview = isAddingToReview,
        isEditingSongs = isEditingSongs,
        isFittingPictures = isFittingPictures,
        syncProgress = syncProgress,
    )
}
