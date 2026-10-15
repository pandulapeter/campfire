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
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.song.Song
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
import com.pandulapeter.campfire.presentation.ui.state.SongLookup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The import queue's consumer against a result reopened from the snackbar's Details, over small fakes of the use cases. Moved
 * with runCurrent rather than advanceUntilIdle, which stops once only background work is left, and all of it is.
 */
class ImportControllerQueueTest {

    private val backStack = mutableStateListOf<CampfireDestination>(CampfireDestination.Songs)
    private var prepareCount = 0
    private var importCount = 0
    private val openedSongs = mutableListOf<String>()
    private val library = MutableStateFlow(SongLookup(songsByFileName = emptyMap(), isLibraryRead = true))
    private var isLibraryIndexedAtOnce = true
    private var plannedSongs = listOf(songEntry("new.cho"))
    private val nextPlans = ArrayDeque<List<ImportPlan.SongEntry>>()
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
            songLookup = library,
            launchFileTransfer = { null },
            isLeaving = { false },
            downloadImportFile = object : DownloadImportFileUseCase {
                override suspend fun invoke(url: String) = ImportDownload.Unreachable
            },
            prepareImport = object : PrepareImportUseCase {
                override suspend fun invoke(files: List<ImportedFile>, onProgress: (ImportProgress) -> Unit): ImportPlan {
                    prepareCount++
                    return ImportPlan(songs = nextPlans.removeFirstOrNull() ?: plannedSongs)
                }
            },
            importFiles = object : ImportFilesUseCase {
                override suspend fun invoke(plan: ImportPlan, choices: ImportChoices, onProgress: (ImportProgress) -> Unit): ImportResult {
                    importCount++
                    if (isLibraryIndexedAtOnce) library.value = library.value.withSongs(plan.songs.map { it.fileName })
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

    private fun SongLookup.withSongs(fileNames: List<String>) = copy(songsByFileName = songsByFileName + fileNames.associateWith(::song))

    private fun song(fileName: String) = Song(
        fileName = fileName, title = fileName, artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
    )

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
        plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))
        startController()
        enqueuePaste()
        runCurrent()
        assertIs<DialogType.NewSongFromText>(dialogHost.visibleDialog.value)
        assertNull(reportState.importReport.value)
        assertFalse(controller.isImporting.value)
        assertEquals(0, importCount)
    }

    @Test
    fun `a pasted song that declares its own title and artist is written and opened, with no form and no review`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "{title: Song}\n{artist: Band}\n[G]Hello"))
        startController()
        enqueuePaste()
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertNull(dialogHost.visibleDialog.value)
        assertNull(reportState.importReport.value)
        assertEquals(1, importCount)
        assertEquals(listOf("new.cho"), openedSongs)
    }

    @Test
    fun `a written song is opened only once the library lookup holds it`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "{title: Song}\n{artist: Band}\n[G]Hello"))
        isLibraryIndexedAtOnce = false
        startController()
        enqueuePaste()
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(1, importCount)
        assertTrue(openedSongs.isEmpty())
        library.value = library.value.withSongs(listOf("new.cho"))
        runCurrent()
        assertEquals(listOf("new.cho"), openedSongs)
    }

    @Test
    fun `a written song is opened at once while the library is still being read, whose screen waits for it`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "{title: Song}\n{artist: Band}\n[G]Hello"))
        isLibraryIndexedAtOnce = false
        library.value = SongLookup(songsByFileName = emptyMap(), isLibraryRead = false)
        startController()
        enqueuePaste()
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(listOf("new.cho"), openedSongs)
    }

    @Test
    fun `a written song the lookup never holds is not opened`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "{title: Song}\n{artist: Band}\n[G]Hello"))
        isLibraryIndexedAtOnce = false
        startController()
        enqueuePaste()
        runCurrent()
        Snapshot.sendApplyNotifications()
        advanceTimeBy(11.seconds)
        library.value = library.value.withSongs(listOf("new.cho"))
        runCurrent()
        assertTrue(openedSongs.isEmpty())
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

    @Test
    fun `texts arriving together are offered one form at a time, each with its own values`() = runTest {
        nextPlans += listOf(songEntry("first.cho", text = "{title: First}\n[G]Hello"))
        nextPlans += listOf(songEntry("second.cho", text = "{title: Second}\n[G]Hello"))
        startController()
        val firstSettled = enqueuePaste()
        val secondSettled = enqueuePaste()
        runCurrent()
        val first = assertIs<DialogType.NewSongFromText>(dialogHost.visibleDialog.value)
        assertEquals("First", first.values[ChordProMetadataFields.Field.TITLE])
        assertEquals(1, prepareCount)
        assertFalse(firstSettled.isCompleted)
        dialogHost.dismissSheet(first)
        runCurrent()
        assertTrue(firstSettled.isCompleted)
        val second = assertIs<DialogType.NewSongFromText>(dialogHost.visibleDialog.value)
        assertEquals("Second", second.values[ChordProMetadataFields.Field.TITLE])
        assertFalse(secondSettled.isCompleted)
        assertEquals(0, importCount)
    }

    @Test
    fun `a text arriving while another dialog is open waits for it to close rather than replacing it`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))
        startController()
        dialogHost.showDialog(DialogType.WhatsNew)
        enqueuePaste()
        runCurrent()
        assertEquals(DialogType.WhatsNew, dialogHost.visibleDialog.value)
        dialogHost.dismissDialog()
        runCurrent()
        assertIs<DialogType.NewSongFromText>(dialogHost.visibleDialog.value)
    }

    @Test
    fun `a text arriving over the onboarding shows its form only once the onboarding is left`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))
        val onboarding = CampfireDestination.Onboarding(isOpenedFromSettings = false)
        backStack += onboarding
        startController()
        enqueuePaste()
        runCurrent()
        assertNull(dialogHost.visibleDialog.value)
        backStack.remove(onboarding)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertIs<DialogType.NewSongFromText>(dialogHost.visibleDialog.value)
    }

    @Test
    fun `a file batch queued behind an open form is not read until the form is closed`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))
        startController()
        enqueuePaste()
        runCurrent()
        val form = assertIs<DialogType.NewSongFromText>(dialogHost.visibleDialog.value)
        enqueue()
        runCurrent()
        assertEquals(1, prepareCount)
        dialogHost.dismissSheet(form)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(2, prepareCount)
    }

    private fun enqueuePaste() = controller.enqueueImport(
        source = ImportSource.Files(listOf(ImportedFile(name = "Pasted.txt", bytes = "Pasted".encodeToByteArray()))),
        isNewSongFromText = true,
    )
}

private fun songEntry(
    fileName: String,
    status: ImportPlan.Status = ImportPlan.Status.NEW,
    isConverted: Boolean = false,
    text: String = "{title: $fileName}",
) = ImportPlan.SongEntry(
    fileName = fileName,
    text = text,
    status = status,
    sourceFileName = fileName,
    isConverted = isConverted,
)
