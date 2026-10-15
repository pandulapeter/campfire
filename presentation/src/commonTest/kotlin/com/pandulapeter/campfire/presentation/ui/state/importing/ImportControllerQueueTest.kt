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
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.domain.api.library.DeleteLibraryUseCase
import com.pandulapeter.campfire.domain.api.importing.DownloadImportFileUseCase
import com.pandulapeter.campfire.domain.api.importing.ImportFilesUseCase
import com.pandulapeter.campfire.domain.api.importing.PrepareImportUseCase
import com.pandulapeter.campfire.domain.api.library.RememberDemoLibraryFilesUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The import queue's consumer against a result reopened from the snackbar's Details, over small fakes of the use cases. Moved
 * with runCurrent rather than advanceUntilIdle, which stops once only background work is left, and all of it is.
 */
class ImportControllerQueueTest {

    private val backStack = mutableStateListOf<CampfireDestination>(CampfireDestination.Songs)
    private var prepareCount = 0
    private var importCount = 0
    private val openedSongs = mutableListOf<String>()
    private var plannedSongs = listOf(songEntry("new.cho"))
    private lateinit var dialogHost: DialogHost
    private lateinit var controller: ImportController
    private lateinit var reportState: ImportReportState

    private fun TestScope.startController() {
        dialogHost = DialogHost(backgroundScope)
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
            openImportedSong = { openedSongs += it },
            launchFileTransfer = { null },
            isLeaving = { false },
            downloadImportFile = object : DownloadImportFileUseCase {
                override suspend fun invoke(url: String) = ImportDownload.Unreachable
            },
            prepareImport = object : PrepareImportUseCase {
                override suspend fun invoke(files: List<ImportedFile>, onProgress: (ImportProgress) -> Unit): ImportPlan {
                    prepareCount++
                    return ImportPlan(songs = plannedSongs)
                }
            },
            importFiles = object : ImportFilesUseCase {
                override suspend fun invoke(plan: ImportPlan, choices: ImportChoices, onProgress: (ImportProgress) -> Unit): ImportResult {
                    importCount++
                    return ImportResult(importedSongFileNames = plan.songs.map { it.fileName })
                }
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

    @Test
    fun `a pasted text that is one new song opens the New song form and writes nothing`() = runTest {
        startController()
        enqueuePaste()
        runCurrent()
        assertIs<DialogType.NewSongFromText>(dialogHost.visibleDialog.value)
        assertNull(reportState.importReport.value)
        assertFalse(controller.isImporting.value)
        assertEquals(0, importCount)
    }

    @Test
    fun `a pasted songbook is reviewed like the file it would be, with no form`() = runTest {
        plannedSongs = listOf(songEntry("one.cho"), songEntry("two.cho"))
        startController()
        enqueuePaste()
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertIs<ImportReport.Review>(reportState.importReport.value)
        assertNull(dialogHost.visibleDialog.value)
        assertEquals(0, importCount)
    }

    @Test
    fun `a pasted song the library already holds is opened, with no form and no review`() = runTest {
        plannedSongs = listOf(songEntry("held.cho", status = ImportPlan.Status.IDENTICAL, isConverted = true))
        startController()
        enqueuePaste()
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertNull(dialogHost.visibleDialog.value)
        assertNull(reportState.importReport.value)
        assertEquals(listOf("held.cho"), openedSongs)
    }

    private fun enqueuePaste() = controller.enqueueImport(
        source = ImportSource.Files(listOf(ImportedFile(name = "Pasted.txt", bytes = "Pasted".encodeToByteArray()))),
        isNewSongFromText = true,
    )
}

private fun songEntry(fileName: String, status: ImportPlan.Status = ImportPlan.Status.NEW, isConverted: Boolean = false) = ImportPlan.SongEntry(
    fileName = fileName,
    text = "{title: $fileName}",
    status = status,
    sourceFileName = fileName,
    isConverted = isConverted,
)
