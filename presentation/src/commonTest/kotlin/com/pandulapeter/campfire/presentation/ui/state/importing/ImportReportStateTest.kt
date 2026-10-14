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
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `a finished report follows a rename and a deletion`() = runTest {
        startState()
        state.report = ImportReport.Finished(ImportResult(importedSongFileNames = listOf("old.cho", "gone.cho", "kept.cho")))
        state.followReportedFileNames { if (it == "old.cho") "new.cho" else it }
        state.followReportedFileNames { it.takeUnless { it == "gone.cho" } }
        assertEquals(listOf("new.cho", "kept.cho"), (state.importReport.value as ImportReport.Finished).result?.importedSongFileNames)
    }
}
