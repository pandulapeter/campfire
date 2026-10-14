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

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import com.pandulapeter.campfire.data.model.domain.ImportChoices
import com.pandulapeter.campfire.data.model.domain.ImportDownload
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.domain.api.library.DeleteLibraryUseCase
import com.pandulapeter.campfire.domain.api.importing.DownloadImportFileUseCase
import com.pandulapeter.campfire.domain.api.importing.ImportFilesUseCase
import com.pandulapeter.campfire.domain.api.importing.PrepareImportUseCase
import com.pandulapeter.campfire.domain.api.library.RememberDemoLibraryFilesUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The import queue's consumer against a result reopened from the snackbar's Details, over small fakes of the use cases. Moved
 * with runCurrent rather than advanceUntilIdle, which stops once only background work is left, and all of it is.
 */
class ImportControllerQueueTest {

    private val backStack = mutableStateListOf<CampfireDestination>(CampfireDestination.Songs)
    private var prepareCount = 0
    private lateinit var controller: ImportController
    private lateinit var reportState: ImportReportState

    private fun TestScope.startController() {
        val dialogHost = DialogHost(backgroundScope)
        reportState = ImportReportState(
            scope = backgroundScope,
            dialogHost = dialogHost,
            backStack = backStack,
            updateBackStack = { update ->
                backStack.update()
                controller.onBackStackChanged()
            },
            songTexts = MutableStateFlow(emptyMap()),
            editorDraft = MutableStateFlow<SongContent?>(null),
            hasUnsavedEditorText = { false },
        )
        controller = ImportController(
            scope = backgroundScope,
            dialogHost = dialogHost,
            messageSink = MessageSink(backgroundScope),
            backStack = backStack,
            reportState = reportState,
            openImportedSong = {},
            launchFileTransfer = { null },
            isLeaving = { false },
            downloadImportFile = object : DownloadImportFileUseCase {
                override suspend fun invoke(url: String) = ImportDownload.Unreachable
            },
            prepareImport = object : PrepareImportUseCase {
                override suspend fun invoke(files: List<ImportedFile>, onProgress: (ImportProgress) -> Unit): ImportPlan {
                    prepareCount++
                    return ImportPlan(
                        songs = listOf(
                            ImportPlan.SongEntry(
                                fileName = "new.cho",
                                text = "{title: New}",
                                status = ImportPlan.Status.NEW,
                                sourceFileName = "new.cho",
                            ),
                        ),
                    )
                }
            },
            importFiles = object : ImportFilesUseCase {
                override suspend fun invoke(plan: ImportPlan, choices: ImportChoices, onProgress: (ImportProgress) -> Unit) =
                    ImportResult(importedSongFileNames = plan.songs.map { it.fileName })
            },
            rememberDemoLibraryFiles = object : RememberDemoLibraryFilesUseCase {
                override suspend fun invoke(songFileNames: Collection<String>, setlistFileNames: Collection<String>) = Unit
            },
            deleteLibrary = object : DeleteLibraryUseCase {
                override suspend fun invoke() = Unit
            },
        )
        controller.startQueue(CompletableDeferred(Unit))
    }

    private fun TestScope.reopenResult() {
        controller.openImportReport(ImportResult(importedSongFileNames = listOf("a.cho")))
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertIs<ImportReport.Finished>(reportState.importReport.value)
        assertTrue(CampfireDestination.ImportReport in backStack)
    }

    private fun TestScope.leaveImportReport() {
        backStack.remove(CampfireDestination.ImportReport)
        controller.onBackStackChanged()
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun enqueue() = controller.enqueueImport(ImportSource.Files(listOf(ImportedFile(name = "new.cho", bytes = "{title: New}".encodeToByteArray()))))

    @Test
    fun `a batch queued while a reopened result is on screen waits until the screen is left`() = runTest {
        startController()
        reopenResult()
        enqueue()
        runCurrent()
        assertIs<ImportReport.Finished>(reportState.importReport.value)
        assertEquals(0, prepareCount)
        leaveImportReport()
        val report = reportState.importReport.value
        assertTrue(report is ImportReport.Loading || report is ImportReport.Review)
        assertEquals(1, prepareCount)
    }

    @Test
    fun `leaving a reopened result does not settle a batch queued meanwhile`() = runTest {
        startController()
        reopenResult()
        val settled = enqueue()
        runCurrent()
        leaveImportReport()
        assertFalse(settled.isCompleted)
        assertIs<ImportReport.Review>(reportState.importReport.value)
        assertTrue(CampfireDestination.ImportReport in backStack)
        leaveImportReport()
        assertTrue(settled.isCompleted)
    }
}
