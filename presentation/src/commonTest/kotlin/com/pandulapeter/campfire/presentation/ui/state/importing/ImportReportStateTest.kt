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

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.ImportPreview
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The import screen's state over a plain back stack, moved with runCurrent like [ImportControllerQueueTest]. */
class ImportReportStateTest {

    private val backStack = mutableStateListOf<CampfireDestination>(CampfireDestination.Songs)
    private val leaves = mutableListOf<Boolean>()
    private lateinit var dialogHost: DialogHost
    private lateinit var state: ImportReportState

    private fun TestScope.startState() {
        dialogHost = DialogHost(backgroundScope)
        state = ImportReportState(
            scope = backgroundScope,
            dialogHost = dialogHost,
            backStack = backStack,
            updateBackStack = { update ->
                backStack.update()
                leaves += state.onBackStackChanged()
            },
            songTexts = MutableStateFlow(emptyMap()),
            editorDraft = MutableStateFlow<SongContent?>(null),
            hasUnsavedEditorText = { false },
        )
    }

    private fun TestScope.settle() {
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private val reportCount get() = backStack.count { it == CampfireDestination.ImportReport }

    @Test
    fun `a report is put up only once no dialog is in the way`() = runTest {
        startState()
        dialogHost.showDialog(DialogType.WhatsNew)
        state.show(ImportReport.Importing)
        settle()
        assertEquals(0, reportCount)
        dialogHost.dismissDialog()
        settle()
        assertEquals(1, reportCount)
    }

    @Test
    fun `a report replaced before it could be put up is not pushed twice`() = runTest {
        startState()
        dialogHost.showDialog(DialogType.WhatsNew)
        state.show(ImportReport.Importing)
        state.show(ImportReport.Finished(null))
        settle()
        dialogHost.dismissDialog()
        settle()
        assertEquals(1, reportCount)
    }

    @Test
    fun `leaving the screen clears its search and says so once`() = runTest {
        startState()
        state.show(ImportReport.Importing)
        settle()
        state.search.textFieldState.setTextAndPlaceCursorAtEnd("query")
        backStack.remove(CampfireDestination.ImportReport)
        leaves += state.onBackStackChanged()
        leaves += state.onBackStackChanged()
        assertEquals(1, leaves.count { it })
        assertEquals("", state.search.textFieldState.text.toString())
    }

    @Test
    fun `the preview of one song takes the place of the import screen that showed it being read`() = runTest {
        startState()
        state.show(ImportReport.Loading(sourceName = "sheet.pdf"))
        settle()
        state.showInPlace(songPreview)
        assertEquals(0, reportCount)
        assertEquals(ImportPreview.singleSong(reviewId = songPreview.id), (backStack.last() as CampfireDestination.SongDetails).importPreview)
        assertTrue(state.isOnBackStack)
        assertTrue(leaves.none { it })
    }

    @Test
    fun `a preview waiting for a dialog goes up in place of the import screen`() = runTest {
        startState()
        dialogHost.showDialog(DialogType.WhatsNew)
        state.show(songPreview)
        settle()
        dialogHost.dismissDialog()
        settle()
        assertEquals(0, reportCount)
        assertEquals(ImportPreview.singleSong(reviewId = songPreview.id), (backStack.last() as CampfireDestination.SongDetails).importPreview)
    }

    @Test
    fun `an imported preview becomes the song's screen in place, which leaves the import`() = runTest {
        startState()
        state.show(songPreview)
        settle()
        val preview = backStack.last() as CampfireDestination.SongDetails
        assertTrue(state.followImportedSong("new.cho"))
        val song = backStack.last() as CampfireDestination.SongDetails
        assertEquals(preview.copy(songFileNames = listOf("new.cho"), importPreview = null), song)
        assertEquals(listOf(false, true), leaves)
    }

    @Test
    fun `the review's rows open in a preview over it, which its Import takes off again`() = runTest {
        startState()
        val review = ImportReport.Review(plan = ImportPlan(songs = listOf(entry, entry)), choices = ImportChoices(), isDemoLibrary = false)
        state.show(review)
        settle()
        state.openReviewPreview(songIndices = listOf(1, 0), index = 1)
        val preview = (backStack.last() as CampfireDestination.SongDetails)
        assertEquals(ImportPreview(songIndices = listOf(1, 0), planSongCount = 2, isReviewPager = true, reviewId = review.id), preview.importPreview)
        assertEquals(1, preview.initialIndex)
        state.closeReviewPreview()
        assertEquals(CampfireDestination.ImportReport, backStack.last())
        assertTrue(leaves.none { it })
    }

    @Test
    fun `a batch joining the review brings the import screen to the front, closing a preview over it`() = runTest {
        startState()
        val review = ImportReport.Review(plan = ImportPlan(songs = listOf(entry, entry)), choices = ImportChoices(), isDemoLibrary = false)
        state.show(review)
        settle()
        state.openReviewPreview(songIndices = listOf(0, 1), index = 0)
        state.bringReviewToFront()
        assertEquals(CampfireDestination.ImportReport, backStack.last())
        assertSame(review, state.importReport.value)
        assertTrue(leaves.none { it })
    }

    private val entry = ImportPlan.SongEntry(fileName = "new.cho", text = "{title: New}", status = ImportPlan.Status.NEW, sourceFileName = "new.cho")

    private val songPreview = ImportReport.Review(plan = ImportPlan(songs = listOf(entry)), choices = ImportChoices(), isDemoLibrary = false, isSongPreview = true)

    @Test
    fun `the review's group choice only touches the rows shown`() = runTest {
        startState()
        val shown = ImportEntryKey(ImportEntryKey.Kind.SONG, 0)
        state.report = ImportReport.Review(plan = ImportPlan(), choices = ImportChoices(), isDemoLibrary = false)
        state.setImportGroupConflictResolution(keys = listOf(shown), isFiltered = true, resolution = ImportConflictResolution.REPLACE)
        val choices = (state.importReport.value as ImportReport.Review).choices
        assertEquals(mapOf(shown to ImportConflictResolution.REPLACE), choices.conflictResolutions)
        assertEquals(ImportChoices().conflictResolution, choices.conflictResolution)
    }

    @Test
    fun `a review keeps its id through a change of its choices, and two reviews have different ones`() = runTest {
        startState()
        val review = ImportReport.Review(plan = ImportPlan(songs = listOf(entry)), choices = ImportChoices(), isDemoLibrary = false)
        state.report = review
        state.setImportEntriesChosen(keys = listOf(ImportEntryKey(ImportEntryKey.Kind.SONG, 0)), isChosen = false)
        val changed = state.importReport.value as ImportReport.Review
        assertTrue(changed.choices != review.choices)
        assertEquals(review.id, changed.id)
        assertTrue(review.id != ImportReport.Review(plan = ImportPlan(songs = listOf(entry)), choices = ImportChoices(), isDemoLibrary = false).id)
    }

    @Test
    fun `a finished report follows a rename and a deletion`() = runTest {
        startState()
        state.report = ImportReport.Finished(ImportResult(importedSongFileNames = listOf("old.cho", "gone.cho", "kept.cho")))
        state.followReportedFileNames { if (it == "old.cho") "new.cho" else it }
        state.followReportedFileNames { it.takeUnless { it == "gone.cho" } }
        assertEquals(listOf("new.cho", "kept.cho"), (state.importReport.value as ImportReport.Finished).result?.importedSongFileNames)
    }
}
