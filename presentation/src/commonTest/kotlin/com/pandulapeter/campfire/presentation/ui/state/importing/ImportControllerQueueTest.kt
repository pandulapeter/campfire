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
import androidx.lifecycle.SavedStateHandle
import com.pandulapeter.campfire.data.model.importing.ImportBookkeeping
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.importing.StagedFileMissingException
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.model.importing.sharedLinkSetlistFileNamesWrittenBy
import com.pandulapeter.campfire.data.model.importing.sharedLinkSongFileNamesWrittenBy
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.SharedLink
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.domain.api.library.DeleteLibraryUseCase
import com.pandulapeter.campfire.domain.api.importing.DownloadImportFileUseCase
import com.pandulapeter.campfire.domain.api.importing.ApplyImportUseCase
import com.pandulapeter.campfire.domain.api.importing.ClearImportStagingUseCase
import com.pandulapeter.campfire.domain.api.importing.DeleteImportStagingUseCase
import com.pandulapeter.campfire.domain.api.importing.PrepareImportUseCase
import com.pandulapeter.campfire.domain.api.library.GetSharedLinkImportUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.ImportPreview
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextTitle
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import com.pandulapeter.campfire.presentation.ui.state.SongLookup
import com.pandulapeter.campfire.presentation.ApplicationScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
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
    private val openedSetlists = mutableListOf<String>()
    private val loadedSongTexts = mutableListOf<String>()
    private val newSongTexts = mutableListOf<String>()
    private val newSongPictures = mutableListOf<String>()
    private val newSongSuggestions = mutableListOf<SharedTextTitle?>()
    private val library = MutableStateFlow(SongLookup(songsByFileName = emptyMap(), isLibraryRead = true))
    private var isLibraryIndexedAtOnce = true
    private var areSongsEnabled = true
    private var onPrepare: () -> Unit = {}
    private var plannedSongs = listOf(songEntry("new.cho"))
    private var plannedSetlists = emptyList<ImportPlan.SetlistEntry>()

    /** What the plans leave out, which makes a batch of one song more than that song, and so reviewed rather than previewed. */
    private var plannedSkipped = emptyList<String>()
    private var plannedImages = emptyList<ImportedFile>()
    private var isImportFailing = false
    private var importFailure: (() -> Exception)? = null
    private val deletedStaging = mutableListOf<StagedFile>()
    private val nextPlans = ArrayDeque<List<ImportPlan.SongEntry>>()
    private var download: ImportDownload = ImportDownload.Unreachable
    private val downloadedUrls = mutableListOf<String>()
    private val preparedFiles = mutableListOf<List<ImportedFile>>()
    private var sharedLinkImports = mapOf<String, Set<String>>()
    private val rememberedSharedLinkImports = mutableListOf<Pair<String, List<String>>>()
    private lateinit var messageSink: MessageSink
    private lateinit var dialogHost: DialogHost
    private lateinit var controller: ImportController
    private lateinit var reportState: ImportReportState

    private lateinit var work: ImportWork

    /** Held open by a test, so that a preparation or a write is still running when it clears the view model. */
    private var prepareGate: CompletableDeferred<Unit>? = null
    private var writeGate: CompletableDeferred<Unit>? = null

    /** Run as a preparation ends however it ends, as the staging a cancelled one leaves is deleted. */
    private var onPrepareEnded: suspend () -> Unit = {}

    /**
     * The view model's part of the import, on [viewScope] (cancelled to clear it), over the one [ImportWork] of the app,
     * built on the first start and kept by the next.
     */
    private fun TestScope.startController(viewScope: CoroutineScope = backgroundScope) {
        if (!::work.isInitialized) work = ImportWork(
            scope = ApplicationScope(CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))),
            useCases = ImportUseCases(
                downloadImportFile = object : DownloadImportFileUseCase {
                    override suspend fun invoke(url: String, onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit) =
                        download.also { downloadedUrls += url }
                },
                prepareImport = object : PrepareImportUseCase {
                    override suspend fun invoke(files: List<ImportedFile>, onProgress: (ImportProgress) -> Unit): ImportPlan {
                        prepareCount++
                        preparedFiles += files
                        onPrepare()
                        try {
                            prepareGate?.await()
                        } finally {
                            onPrepareEnded()
                        }
                        return ImportPlan(songs = nextPlans.removeFirstOrNull() ?: plannedSongs, setlists = plannedSetlists, skippedFileNames = plannedSkipped, imageFiles = plannedImages)
                    }
                },
                applyImport = object : ApplyImportUseCase {
                    override suspend fun invoke(
                        plan: ImportPlan,
                        choices: ImportChoices,
                        bookkeeping: ImportBookkeeping,
                        onProgress: (ImportProgress) -> Unit,
                    ): ImportResult {
                        importCount++
                        writeGate?.await()
                        if (isImportFailing) error("The disk is full.")
                        importFailure?.let { throw it() }
                        // A kept-both conflict lands under a numbered name, as the storage layer writes it.
                        val written = plan.songs.mapIndexed { index, entry ->
                            val isKeptBoth = entry.status == ImportPlan.Status.CONFLICTING &&
                                choices.resolutionOf(ImportEntryKey(ImportEntryKey.Kind.SONG, index)) == ImportConflictResolution.KEEP_BOTH
                            if (isKeptBoth) entry.fileName.replace(".cho", "_2.cho") else entry.fileName
                        }
                        if (isLibraryIndexedAtOnce) library.value = library.value.withSongs(written)
                        val result = ImportResult(importedSongFileNames = written)
                        bookkeeping.sharedLinkParts.forEach { part ->
                            val linkPlan = part.of(plan)
                            rememberedSharedLinkImports += part.fileId to
                                (sharedLinkSongFileNamesWrittenBy(linkPlan, result) + sharedLinkSetlistFileNamesWrittenBy(linkPlan, result))
                        }
                        return result
                    }
                },
                getSharedLinkImport = object : GetSharedLinkImportUseCase {
                    override suspend fun invoke(linkFileId: String) = sharedLinkImports[linkFileId].orEmpty()
                },
                clearImportStaging = object : ClearImportStagingUseCase {
                    override suspend fun invoke() = Unit
                },
                deleteImportStaging = object : DeleteImportStagingUseCase {
                    override suspend fun invoke(files: Collection<StagedFile>) {
                        deletedStaging += files
                    }
                },
            ),
        )
        dialogHost = DialogHost(viewScope)
        messageSink = MessageSink(viewScope)
        reportState = ImportReportState(
            scope = viewScope,
            dialogHost = dialogHost,
            backStack = backStack,
            updateBackStack = { update ->
                backStack.update()
                controller.onBackStackChanged()
            },
            songTexts = MutableStateFlow(emptyMap()),
            editorDraft = MutableStateFlow<SongContent?>(null),
            hasUnsavedEditorText = { false },
            savedStateStore = SavedStateStore(SavedStateHandle(), viewScope),
            messageSink = messageSink,
            isImportCarriedOver = work.isImporting.value,
        )
        controller = ImportController(
            scope = viewScope,
            dialogHost = dialogHost,
            messageSink = messageSink,
            backStack = backStack,
            reportState = reportState,
            openLibraryItem = { item ->
                when (item) {
                    is LibraryItemToOpen.Song -> openedSongs += item.fileName
                    is LibraryItemToOpen.Setlist -> openedSetlists += item.fileName
                }
            },
            loadSongText = { loadedSongTexts += it },
            openNewSongEditor = { song, suggestion ->
                newSongTexts += song.text
                newSongSuggestions += suggestion
                newSongPictures += song.images.map { it.source }
                backStack.removeAll { it is CampfireDestination.SongEditor }
                backStack += CampfireDestination.SongEditor.newSong()
                true
            },
            areSongsEnabled = { areSongsEnabled },
            songLookup = library,
            launchFileTransfer = { null },
            isLeaving = { false },
            work = work,
            deleteLibrary = object : DeleteLibraryUseCase {
                override suspend fun invoke() = Unit
            },
        )
        controller.startQueue(CompletableDeferred(Unit))
    }

    /**
     * Clears the view model as Android does when its activity finishes while the process goes on, its screens with it,
     * and starts the next one over the same [work].
     */
    private fun TestScope.clearViewModelAndStartAnother(viewScope: CoroutineScope) {
        viewScope.cancel()
        runCurrent()
        backStack.removeAll { it != CampfireDestination.Songs }
        startController(CoroutineScope(backgroundScope.coroutineContext + Job()))
        Snapshot.sendApplyNotifications()
        runCurrent()
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
        plannedSkipped = listOf("notes.txt")
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
        plannedSkipped = listOf("notes.txt")
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
    fun `a pasted text that is one new song opens the editor on it and writes nothing`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))
        startController()
        val settled = enqueuePaste()
        runCurrent()
        assertEquals(listOf("[G]Hello"), newSongTexts)
        assertTrue(backStack.last().let { it is CampfireDestination.SongEditor && it.isNewSong })
        assertTrue(settled.isCompleted)
        assertNull(reportState.importReport.value)
        assertFalse(controller.isImporting.value)
        assertEquals(0, importCount)
    }

    @Test
    fun `a pasted ABC tune opens the editor, and one with no chords is said to keep its notation`() = runTest {
        val tune = "{title: Lantern Reel}\n\n{start_of_abc}\nX:1\nT:Lantern Reel\nK:G\nGABc|\n{end_of_abc}\n"
        plannedSongs = listOf(songEntry("new.cho", text = tune, isConverted = true))
        startController()
        enqueuePaste()
        runCurrent()
        assertEquals(listOf(tune), newSongTexts)
        assertEquals(
            listOf<Message>(Message.NewSongFromText(isConverted = true, hasChords = false, hasNotation = true)),
            messageSink.messageQueue.value.map { it.value },
        )
    }

    @Test
    fun `a pasted song of lyrics alone is not said to keep notation`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "{title: Song}\nHello"))
        startController()
        enqueuePaste()
        runCurrent()
        assertEquals(
            listOf<Message>(Message.NewSongFromText(isConverted = false, hasChords = false, hasNotation = false)),
            messageSink.messageQueue.value.map { it.value },
        )
    }

    @Test
    fun `a new song from a share opens the editor with its suggestion`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))
        startController()
        val suggestion = SharedTextTitle(title = "Riverlight", artist = "The Invented Band")
        enqueuePaste(suggestion)
        runCurrent()
        assertEquals(listOf<SharedTextTitle?>(suggestion), newSongSuggestions)
    }

    @Test
    fun `a pasted song that declares its own title and artist opens the editor too`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "{title: Song}\n{artist: Band}\n[G]Hello"))
        startController()
        enqueuePaste()
        runCurrent()
        assertEquals(listOf("{title: Song}\n{artist: Band}\n[G]Hello"), newSongTexts)
        assertEquals(0, importCount)
        assertTrue(openedSongs.isEmpty())
    }

    @Test
    fun `with the songs switched off a pasted song is reviewed and written only once imported`() = runTest {
        areSongsEnabled = false
        plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))
        startController()
        val settled = enqueuePaste()
        assertReviewedAndWrittenOnceImported(settled)
    }

    @Test
    fun `with the songs switched off a song handed over is reviewed, written once imported and not opened`() = runTest {
        areSongsEnabled = false
        startController()
        val settled = handOver()
        assertReviewedAndWrittenOnceImported(settled)
    }

    private fun TestScope.assertReviewedAndWrittenOnceImported(settled: CompletableDeferred<Unit>) {
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertIs<ImportReport.Review>(reportState.importReport.value)
        assertEquals(0, importCount)
        assertTrue(newSongTexts.isEmpty())
        controller.confirmImport()
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(1, importCount)
        assertTrue(openedSongs.isEmpty())
        assertFalse(CampfireDestination.ImportReport in backStack)
        assertTrue(settled.isCompleted)
    }

    @Test
    fun `a switch turned on while a songs-off share is read still ends in its review`() = runTest {
        areSongsEnabled = false
        onPrepare = { areSongsEnabled = true }
        plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))
        startController()
        enqueuePaste()
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertIs<ImportReport.Review>(reportState.importReport.value)
        assertTrue(newSongTexts.isEmpty())
        assertEquals(0, importCount)
        controller.confirmImport()
        runCurrent()
        assertEquals(1, importCount)
        assertFalse(controller.isImporting.value)
    }

    @Test
    fun `a switch turned off while a share is read ends in its review, not in the editor`() = runTest {
        onPrepare = { areSongsEnabled = false }
        plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))
        startController()
        enqueuePaste()
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertIs<ImportReport.Review>(reportState.importReport.value)
        assertTrue(newSongTexts.isEmpty())
        assertTrue(backStack.none { it is CampfireDestination.SongEditor })
        assertEquals(0, importCount)
    }

    @Test
    fun `a song handed over is previewed, and nothing is written until it is imported`() = runTest {
        startController()
        val settled = handOver()
        settle()
        val review = assertIs<ImportReport.Review>(reportState.importReport.value)
        assertTrue(review.isSongPreview)
        assertEquals(ImportPreview.singleSong(reviewId = review.id), (backStack.last() as CampfireDestination.SongDetails).importPreview)
        assertFalse(CampfireDestination.ImportReport in backStack)
        assertEquals(0, importCount)
        assertFalse(settled.isCompleted)
    }

    @Test
    fun `a song picked from Import files is previewed as well`() = runTest {
        startController()
        enqueue()
        settle()
        assertTrue(assertIs<ImportReport.Review>(reportState.importReport.value).isSongPreview)
        assertTrue(backStack.last().isSongPreview)
    }

    @Test
    fun `leaving a preview writes nothing and lets the queue go on`() = runTest {
        startController()
        val settled = handOver()
        settle()
        backStack.removeAt(backStack.lastIndex)
        controller.onBackStackChanged()
        settle()
        assertEquals(0, importCount)
        assertNull(reportState.importReport.value)
        assertTrue(settled.isCompleted)
    }

    @Test
    fun `an imported preview becomes the song's own screen once the library holds the song`() = runTest {
        isLibraryIndexedAtOnce = false
        startController()
        handOver()
        settle()
        val preview = backStack.last() as CampfireDestination.SongDetails
        controller.importFromPreview(ImportPreviewAction.IMPORT)
        settle()
        assertEquals(1, importCount)
        assertSame(ImportReport.Importing, reportState.importReport.value)
        assertSame(preview, backStack.last())
        library.value = library.value.withSongs(listOf("new.cho"))
        settle()
        val song = backStack.last() as CampfireDestination.SongDetails
        assertEquals(listOf("new.cho"), song.songFileNames)
        assertNull(song.importPreview)
        assertEquals(preview.id, song.id)
        assertEquals(listOf("new.cho"), loadedSongTexts)
        assertNull(reportState.importReport.value)
        assertTrue(openedSongs.isEmpty())
        assertEquals(listOf<Message>(Message.PreviewedSongImported), messageSink.messageQueue.value.map { it.value })
    }

    @Test
    fun `a preview left while its song is written is said the short way and opens nothing`() = runTest {
        isLibraryIndexedAtOnce = false
        startController()
        handOver()
        settle()
        controller.importFromPreview(ImportPreviewAction.IMPORT)
        settle()
        backStack.removeAt(backStack.lastIndex)
        controller.onBackStackChanged()
        library.value = library.value.withSongs(listOf("new.cho"))
        settle()
        assertTrue(backStack.none { it is CampfireDestination.SongDetails })
        assertTrue(openedSongs.isEmpty())
        assertIs<Message.ImportFinished>(messageSink.messageQueue.value.single().value)
    }

    @Test
    fun `a preview of a taken name imports as a copy, numbered`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", status = ImportPlan.Status.CONFLICTING))
        startController()
        handOver()
        settle()
        controller.importFromPreview(ImportPreviewAction.IMPORT_AS_COPY)
        settle()
        assertEquals(listOf("new_2.cho"), (backStack.last() as CampfireDestination.SongDetails).songFileNames)
    }

    @Test
    fun `a preview of a taken name replaces only once that is confirmed`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", status = ImportPlan.Status.CONFLICTING))
        startController()
        handOver()
        settle()
        controller.importFromPreview(ImportPreviewAction.REPLACE)
        settle()
        assertIs<DialogType.ConfirmImportReplace>(dialogHost.visibleDialog.value)
        assertEquals(0, importCount)
        controller.resolveImport()
        dialogHost.dismissDialog()
        settle()
        assertEquals(listOf("new.cho"), (backStack.last() as CampfireDestination.SongDetails).songFileNames)
    }

    @Test
    fun `a preview whose song could not be written stays up to be imported again`() = runTest {
        isImportFailing = true
        startController()
        handOver()
        settle()
        controller.importFromPreview(ImportPreviewAction.IMPORT)
        settle()
        assertTrue(assertIs<ImportReport.Review>(reportState.importReport.value).isSongPreview)
        assertTrue(backStack.last().isSongPreview)
        assertEquals(listOf<Message>(Message.ImportFailed), messageSink.messageQueue.value.map { it.value })
        isImportFailing = false
        controller.importFromPreview(ImportPreviewAction.IMPORT)
        settle()
        assertEquals(listOf("new.cho"), (backStack.last() as CampfireDestination.SongDetails).songFileNames)
    }

    @Test
    fun `a batch arriving while a preview is up joins it, putting the import screen in its place`() = runTest {
        startController()
        handOver()
        settle()
        val previewIndex = backStack.lastIndex
        nextPlans += listOf(songEntry("other.cho"))
        val settled = enqueue()
        settle()
        val joined = assertIs<ImportReport.Review>(reportState.importReport.value)
        assertFalse(joined.isSongPreview)
        assertEquals(listOf("new.cho", "other.cho"), joined.plan.songs.map { it.fileName })
        assertEquals(previewIndex, backStack.lastIndex)
        assertEquals(CampfireDestination.ImportReport, backStack.last())
        assertFalse(settled.isCompleted)
        leaveImportReport()
        assertTrue(settled.isCompleted)
    }

    @Test
    fun `a song handed over that the library already holds is opened rather than previewed, and said to be there`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", status = ImportPlan.Status.IDENTICAL))
        startController()
        handOver()
        settle()
        assertEquals(listOf("new.cho"), openedSongs)
        assertNull(reportState.importReport.value)
        assertTrue(backStack.none { it.isSongPreview })
        assertEquals(listOf<Message>(Message.AlreadyInLibrary), messageSink.messageQueue.value.map { it.value })
    }

    @Test
    fun `a library song is opened only once the library lookup holds it`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", status = ImportPlan.Status.IDENTICAL))
        isLibraryIndexedAtOnce = false
        startController()
        handOver()
        settle()
        assertTrue(openedSongs.isEmpty())
        library.value = library.value.withSongs(listOf("new.cho"))
        runCurrent()
        assertEquals(listOf("new.cho"), openedSongs)
    }

    @Test
    fun `a pasted songbook is reviewed like the file it would be, with no editor`() = runTest {
        plannedSongs = listOf(songEntry("one.cho"), songEntry("two.cho"))
        startController()
        enqueuePaste()
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertIs<ImportReport.Review>(reportState.importReport.value)
        assertTrue(newSongTexts.isEmpty())
        assertEquals(0, importCount)
    }

    @Test
    fun `a pasted song the library already holds is opened, with no editor and no review`() = runTest {
        plannedSongs = listOf(songEntry("held.cho", status = ImportPlan.Status.IDENTICAL, isConverted = true))
        startController()
        enqueuePaste()
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertTrue(newSongTexts.isEmpty())
        assertNull(reportState.importReport.value)
        assertEquals(listOf("held.cho"), openedSongs)
    }

    @Test
    fun `texts arriving together are opened one editor at a time, the next once the editor is left`() = runTest {
        nextPlans += listOf(songEntry("first.cho", text = "{title: First}\n[G]Hello"))
        nextPlans += listOf(songEntry("second.cho", text = "{title: Second}\n[G]Hello"))
        startController()
        val firstSettled = enqueuePaste()
        val secondSettled = enqueuePaste()
        runCurrent()
        assertEquals(listOf("{title: First}\n[G]Hello"), newSongTexts)
        assertEquals(1, prepareCount)
        assertTrue(firstSettled.isCompleted)
        assertFalse(secondSettled.isCompleted)
        leaveEditor()
        assertEquals(listOf("{title: First}\n[G]Hello", "{title: Second}\n[G]Hello"), newSongTexts)
        assertTrue(secondSettled.isCompleted)
        assertEquals(0, importCount)
    }

    @Test
    fun `a text arriving while a dialog is open waits for it to close`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))
        startController()
        dialogHost.showDialog(DialogType.WhatsNew)
        enqueuePaste()
        runCurrent()
        assertTrue(newSongTexts.isEmpty())
        dialogHost.dismissDialog()
        runCurrent()
        assertEquals(listOf("[G]Hello"), newSongTexts)
    }

    @Test
    fun `a text arriving over the onboarding opens the editor only once the onboarding is left`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))
        val onboarding = CampfireDestination.Onboarding(isOpenedFromSettings = false)
        backStack += onboarding
        startController()
        enqueuePaste()
        runCurrent()
        assertTrue(newSongTexts.isEmpty())
        backStack.remove(onboarding)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(listOf("[G]Hello"), newSongTexts)
    }

    @Test
    fun `a file batch queued behind a new song's editor is not read until the editor is left`() = runTest {
        plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))
        startController()
        enqueuePaste()
        runCurrent()
        enqueue()
        runCurrent()
        assertEquals(1, prepareCount)
        leaveEditor()
        assertEquals(2, prepareCount)
    }

    @Test
    fun `a shared link's taken name it wrote before starts on Replace`() = runTest {
        download = ImportDownload.File(name = "abcdefghijklmnop.zip", bytes = ZIP_BYTES)
        plannedSongs = listOf(songEntry("riverlight.cho", status = ImportPlan.Status.CONFLICTING))
        sharedLinkImports = mapOf(sharedLink.fileId to setOf("songs/riverlight.cho"))
        startController()
        controller.importSharedLink(sharedLink)
        settle()
        assertEquals(listOf(sharedLink.downloadUrl), downloadedUrls)
        assertEquals(listOf("abcdefghijklmnop.zip"), preparedFiles.single().map { it.name })
        val review = assertIs<ImportReport.Review>(reportState.importReport.value)
        assertEquals(ImportConflictResolution.REPLACE, review.choices.resolutionOf(ImportEntryKey(ImportEntryKey.Kind.SONG, 0)))
        assertEquals(1, review.sharedLinkUpdateCount)
        assertTrue(review.isSharedLink)
        controller.confirmImport()
        assertIs<DialogType.ConfirmImportReplace>(dialogHost.visibleDialog.value)
    }

    @Test
    fun `a shared link's taken name it never wrote keeps both`() = runTest {
        download = ImportDownload.File(name = "abcdefghijklmnop.zip", bytes = ZIP_BYTES)
        plannedSongs = listOf(songEntry("riverlight.cho", status = ImportPlan.Status.CONFLICTING))
        // A crafted link copies the share id of a link the recipient imported, but serves another file.
        sharedLinkImports = mapOf(sharedLink.fileId to setOf("songs/riverlight.cho"))
        val crafted = assertIs<SharedLink.Readable>(
            ShareLinks.create(kind = SharedLinkKind.SONG, shareId = sharedLink.shareId, fileId = "craftedfileid", rlkey = "rlkey", title = "Riverlight"),
        )
        startController()
        controller.importSharedLink(crafted)
        settle()
        val review = assertIs<ImportReport.Review>(reportState.importReport.value)
        assertEquals(ImportConflictResolution.KEEP_BOTH, review.choices.resolutionOf(ImportEntryKey(ImportEntryKey.Kind.SONG, 0)))
        assertEquals(0, review.sharedLinkUpdateCount)
        controller.confirmImport()
        assertNull(dialogHost.visibleDialog.value)
    }

    @Test
    fun `a shared link's import remembers what it wrote under its own names`() = runTest {
        download = ImportDownload.File(name = "abcdefghijklmnop.zip", bytes = ZIP_BYTES)
        plannedSongs = listOf(
            songEntry("riverlight.cho", status = ImportPlan.Status.CONFLICTING),
            songEntry("mine.cho", status = ImportPlan.Status.CONFLICTING),
        )
        sharedLinkImports = mapOf(sharedLink.fileId to setOf("songs/riverlight.cho"))
        startController()
        controller.importSharedLink(sharedLink)
        settle()
        controller.confirmImport()
        assertIs<DialogType.ConfirmImportReplace>(dialogHost.visibleDialog.value)
        controller.resolveImport()
        settle()
        assertEquals(listOf(sharedLink.fileId to listOf("riverlight.cho")), rememberedSharedLinkImports)
    }

    @Test
    fun `a shared setlist the library holds as it is, songs and all, is opened rather than reviewed, and said to be there`() = runTest {
        download = ImportDownload.File(name = "abcdefghijklmnop.zip", bytes = ZIP_BYTES)
        plannedSongs = listOf(songEntry("riverlight.cho", status = ImportPlan.Status.IDENTICAL))
        plannedSetlists = listOf(setlistEntry("gig.setlist.json", status = ImportPlan.Status.IDENTICAL))
        startController()
        controller.importSharedLink(sharedLink)
        settle()
        assertEquals(listOf("gig.setlist.json"), openedSetlists)
        assertTrue(openedSongs.isEmpty())
        assertNull(reportState.importReport.value)
        assertTrue(CampfireDestination.ImportReport !in backStack)
        assertEquals(listOf<Message>(Message.AlreadyInLibrary), messageSink.messageQueue.value.map { it.value })
    }

    @Test
    fun `a shared setlist the library holds with one of its songs changed is reviewed`() = runTest {
        download = ImportDownload.File(name = "abcdefghijklmnop.zip", bytes = ZIP_BYTES)
        plannedSongs = listOf(songEntry("riverlight.cho", status = ImportPlan.Status.CONFLICTING))
        plannedSetlists = listOf(setlistEntry("gig.setlist.json", status = ImportPlan.Status.IDENTICAL))
        startController()
        controller.importSharedLink(sharedLink)
        settle()
        assertIs<ImportReport.Review>(reportState.importReport.value)
        assertTrue(openedSetlists.isEmpty())
    }

    @Test
    fun `a file imported any other way keeps both by default`() = runTest {
        plannedSongs = listOf(songEntry("riverlight.cho", status = ImportPlan.Status.CONFLICTING))
        startController()
        enqueue()
        settle()
        val review = assertIs<ImportReport.Review>(reportState.importReport.value)
        assertEquals(ImportConflictResolution.KEEP_BOTH, review.choices.conflictResolution)
        assertFalse(review.isSharedLink)
    }

    @Test
    fun `a shared link answered with a page fails as a link that no longer works, and nothing is read`() = runTest {
        download = ImportDownload.File(name = "abcdefghijklmnop.zip", bytes = "<html>Error</html>".encodeToByteArray())
        startController()
        controller.importSharedLink(sharedLink)
        settle()
        val failed = assertIs<ImportReport.Failed>(reportState.importReport.value)
        assertEquals(ImportFailure.LINK_GONE, failed.reason)
        assertEquals("Riverlight", failed.sourceName)
        assertEquals(0, prepareCount)
    }

    @Test
    fun `a preparation refused for space fails naming the space needed, and Try again reads the batch again`() = runTest {
        download = ImportDownload.File(name = "abcdefghijklmnop.zip", bytes = ZIP_BYTES)
        onPrepare = { throw StagingRefusedException.NoSpace(neededBytes = 2_400_000_000) }
        startController()
        controller.importSharedLink(sharedLink)
        settle()
        val failed = assertIs<ImportReport.Failed>(reportState.importReport.value)
        assertEquals(ImportFailure.NO_SPACE, failed.reason)
        assertEquals(2_400_000_000, failed.neededBytes)
        onPrepare = {}
        controller.retryImport()
        settle()
        assertIs<ImportReport.Review>(reportState.importReport.value)
        assertEquals(2, prepareCount)
    }

    @Test
    fun `what a batch staged is deleted once it has been imported, and not while it is reviewed or written`() = runTest {
        plannedSkipped = listOf("notes.txt")
        plannedSongs = listOf(songEntry("new.cho").withStagedPicture())
        val gate = CompletableDeferred<Unit>().also { writeGate = it }
        startController()
        controller.enqueueImport(ImportSource.Files(listOf(STAGED_ARCHIVE)))
        settle()
        assertIs<ImportReport.Review>(reportState.importReport.value)
        assertTrue(deletedStaging.isEmpty())
        controller.resolveImport()
        settle()
        assertTrue(deletedStaging.isEmpty())
        gate.complete(Unit)
        settle()
        assertEquals(1, importCount)
        assertEquals(setOf(STAGED_ARCHIVE.staged!!, STAGED_PICTURE), deletedStaging.toSet())
    }

    @Test
    fun `what a batch staged is deleted once its review is left unanswered`() = runTest {
        plannedSkipped = listOf("notes.txt")
        plannedSongs = listOf(songEntry("new.cho").withStagedPicture())
        startController()
        controller.enqueueImport(ImportSource.Files(listOf(STAGED_ARCHIVE)))
        settle()
        leaveImportReport()
        settle()
        assertEquals(0, importCount)
        assertEquals(setOf(STAGED_ARCHIVE.staged!!, STAGED_PICTURE), deletedStaging.toSet())
    }

    @Test
    fun `an Import that finds a staged file gone fails writing nothing, and Try again reads the staged batch again`() = runTest {
        plannedSkipped = listOf("notes.txt")
        plannedSongs = listOf(songEntry("new.cho").withStagedPicture())
        importFailure = { StagedFileMissingException(STAGED_PICTURE.name) }
        startController()
        controller.enqueueImport(ImportSource.Files(listOf(STAGED_ARCHIVE)))
        settle()
        controller.resolveImport()
        settle()
        assertEquals(ImportFailure.UNREADABLE, assertIs<ImportReport.Failed>(reportState.importReport.value).reason)
        assertTrue(deletedStaging.isEmpty())
        importFailure = null
        controller.retryImport()
        settle()
        assertEquals(listOf(listOf(STAGED_ARCHIVE), listOf(STAGED_ARCHIVE)), preparedFiles)
        controller.resolveImport()
        settle()
        assertEquals(2, importCount)
        assertEquals(setOf(STAGED_ARCHIVE.staged!!, STAGED_PICTURE), deletedStaging.toSet())
    }

    @Test
    fun `a shared link that could not be reached is downloaded again on Try again`() = runTest {
        startController()
        controller.importSharedLink(sharedLink)
        settle()
        assertEquals(ImportFailure.LINK_UNREACHABLE, assertIs<ImportReport.Failed>(reportState.importReport.value).reason)
        download = ImportDownload.File(name = "abcdefghijklmnop.zip", bytes = ZIP_BYTES)
        controller.retryImport()
        settle()
        assertEquals(2, downloadedUrls.size)
        assertIs<ImportReport.Review>(reportState.importReport.value)
    }

    @Test
    fun `a link from a newer version is answered with a sentence and queues nothing`() = runTest {
        startController()
        val settled = controller.importSharedLink(assertIs<SharedLink.FromNewerVersion>(ShareLinks.parse("campfire://s/2.something.new")))
        settle()
        assertTrue(settled.isCompleted)
        assertTrue(downloadedUrls.isEmpty())
        assertEquals(listOf<Message>(Message.SharedLinkFromNewerVersion), messageSink.messageQueue.value.map { it.value })
    }

    @Test
    fun `a batch arriving during a review is added to it rather than waiting for a screen of its own`() = runTest {
        plannedSkipped = listOf("notes.txt")
        startController()
        enqueue()
        settle()
        reportState.setImportEntriesChosen(listOf(ImportEntryKey(ImportEntryKey.Kind.SONG, 0)), isChosen = false)
        nextPlans += listOf(songEntry("other.cho"))
        val settled = enqueue()
        settle()
        val joined = assertIs<ImportReport.Review>(reportState.importReport.value)
        assertEquals(listOf("new.cho", "other.cho"), joined.plan.songs.map { it.fileName })
        assertEquals(setOf(ImportEntryKey(ImportEntryKey.Kind.SONG, 0)), joined.choices.leftOut)
        assertEquals(1, backStack.count { it == CampfireDestination.ImportReport })
        assertFalse(settled.isCompleted)
        assertFalse(controller.isAddingToReview.value)
        leaveImportReport()
        assertTrue(settled.isCompleted)
        assertNull(reportState.importReport.value)
    }

    @Test
    fun `the same files dropped twice during a review leave it as it was`() = runTest {
        plannedSkipped = listOf("notes.txt")
        startController()
        enqueue()
        settle()
        val review = assertIs<ImportReport.Review>(reportState.importReport.value)
        enqueue()
        settle()
        assertEquals(2, prepareCount)
        assertSame(review, reportState.importReport.value)
        assertEquals(0, messageSink.messageQueue.value.size)
    }

    @Test
    fun `a batch added to a review is written with it`() = runTest {
        plannedSkipped = listOf("notes.txt")
        startController()
        enqueue()
        settle()
        nextPlans += listOf(songEntry("other.cho"))
        enqueue()
        settle()
        controller.confirmImport()
        settle()
        assertEquals(1, importCount)
        assertEquals(setOf("new.cho", "other.cho"), library.value.songsByFileName.keys)
    }

    @Test
    fun `a pasted song arriving during a review waits for it rather than joining it`() = runTest {
        plannedSkipped = listOf("notes.txt")
        startController()
        enqueue()
        settle()
        nextPlans += listOf(songEntry("pasted.cho", text = "[G]Hello"))
        plannedSkipped = emptyList()
        enqueuePaste()
        settle()
        assertEquals(1, prepareCount)
        assertEquals(listOf("new.cho"), assertIs<ImportReport.Review>(reportState.importReport.value).plan.songs.map { it.fileName })
        leaveImportReport()
        assertEquals(listOf("[G]Hello"), newSongTexts)
    }

    @Test
    fun `a batch arriving while Replace is being confirmed joins the review once the confirmation is cancelled`() = runTest {
        plannedSkipped = listOf("notes.txt")
        startController()
        enqueue()
        settle()
        dialogHost.showDialog(DialogType.ConfirmImportReplace(1))
        nextPlans += listOf(songEntry("other.cho"))
        enqueue()
        settle()
        assertEquals(listOf("new.cho"), assertIs<ImportReport.Review>(reportState.importReport.value).plan.songs.map { it.fileName })
        assertEquals(1, prepareCount)
        assertFalse(controller.isAddingToReview.value)
        dialogHost.dismissDialog()
        settle()
        assertEquals(listOf("new.cho", "other.cho"), assertIs<ImportReport.Review>(reportState.importReport.value).plan.songs.map { it.fileName })
    }

    @Test
    fun `a batch arriving while Replace is being confirmed is not written by it, and is imported after it`() = runTest {
        plannedSkipped = listOf("notes.txt")
        startController()
        enqueue()
        settle()
        dialogHost.showDialog(DialogType.ConfirmImportReplace(1))
        nextPlans += listOf(songEntry("other.cho"))
        enqueue()
        settle()
        controller.resolveImport()
        dialogHost.dismissDialog()
        settle()
        assertEquals(1, importCount)
        assertEquals(setOf("new.cho"), library.value.songsByFileName.keys)
        if (reportState.importReport.value !is ImportReport.Review) leaveImportReport()
        settle()
        assertEquals(listOf("other.cho"), assertIs<ImportReport.Review>(reportState.importReport.value).plan.songs.map { it.fileName })
    }

    private val sharedLink = assertIs<SharedLink.Readable>(
        ShareLinks.create(kind = SharedLinkKind.SONG, shareId = "abcdefghijklmnop", fileId = "fileid", rlkey = "rlkey", title = "Riverlight"),
    )

    private fun TestScope.settle() {
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun TestScope.leaveEditor() {
        backStack.removeAll { it is CampfireDestination.SongEditor }
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun handOver() = controller.importFiles(listOf(ImportedFile(name = "new.cho", bytes = "{title: New}".encodeToByteArray())))

    @Test
    fun `pictures dropped alone open the editor on one new song showing them, and write nothing`() = runTest {
        val pictures = listOf(ImportedFile(name = "page 1.jpg", bytes = byteArrayOf(1)), ImportedFile(name = "page 2.png", bytes = byteArrayOf(2)))
        plannedSongs = emptyList()
        plannedImages = pictures
        startController()
        val settled = controller.enqueueImport(ImportSource.Files(pictures), shouldOpenSong = true)
        runCurrent()
        assertEquals(listOf("page 1.jpg", "page 2.png"), newSongPictures)
        assertTrue(backStack.last().let { it is CampfireDestination.SongEditor && it.isNewSong })
        assertTrue(settled.isCompleted)
        assertNull(reportState.importReport.value)
        assertEquals(0, importCount)
        // Nothing was converted from a text, so nothing is said about how one was read.
        assertTrue(messageSink.messageQueue.value.isEmpty())
    }

    @Test
    fun `a picture dropped beside a song is reported as skipped in the review`() = runTest {
        plannedImages = listOf(ImportedFile(name = "photo.jpg", bytes = byteArrayOf(1)))
        startController()
        controller.enqueueImport(ImportSource.Files(listOf(ImportedFile(name = "new.cho", bytes = byteArrayOf(1)), plannedImages.single())))
        runCurrent()
        assertTrue(newSongPictures.isEmpty())
        assertEquals(listOf("photo.jpg"), assertIs<ImportReport.Review>(reportState.importReport.value).plan.skippedFileNames)
    }

    @Test
    fun `a preparation outlives a cleared view model and opens its review in the next one`() = runTest {
        val gate = CompletableDeferred<Unit>().also { prepareGate = it }
        val viewScope = CoroutineScope(backgroundScope.coroutineContext + Job())
        startController(viewScope)
        val settled = enqueueTwoSongs()
        settle()
        assertIs<ImportReport.Loading>(reportState.importReport.value)
        assertEquals(ImportWorkState.UNPACKING, work.state.value)
        clearViewModelAndStartAnother(viewScope)
        assertEquals(ImportWorkState.UNPACKING, work.state.value)
        assertIs<ImportReport.Loading>(reportState.importReport.value)
        assertTrue(CampfireDestination.ImportReport in backStack)
        assertTrue(controller.isImporting.value)
        assertFalse(settled.isCompleted)
        assertEquals(1, controller.queuedImportCount.value)
        gate.complete(Unit)
        settle()
        assertEquals(1, prepareCount)
        assertEquals(ImportWorkState.IDLE, work.state.value)
        assertIs<ImportReport.Review>(reportState.importReport.value)
        controller.confirmImport()
        settle()
        assertEquals(1, importCount)
        leaveImportReport()
        assertTrue(settled.isCompleted)
        assertEquals(0, controller.queuedImportCount.value)
    }

    @Test
    fun `a preparation that finished while nobody watched opens its review when the next view model starts`() = runTest {
        val gate = CompletableDeferred<Unit>().also { prepareGate = it }
        val viewScope = CoroutineScope(backgroundScope.coroutineContext + Job())
        startController(viewScope)
        val settled = enqueueTwoSongs()
        settle()
        viewScope.cancel()
        gate.complete(Unit)
        settle()
        assertEquals(ImportWorkState.IDLE, work.state.value)
        assertFalse(settled.isCompleted)
        backStack.removeAll { it != CampfireDestination.Songs }
        startController(CoroutineScope(backgroundScope.coroutineContext + Job()))
        settle()
        assertEquals(1, prepareCount)
        assertIs<ImportReport.Review>(reportState.importReport.value)
        assertTrue(CampfireDestination.ImportReport in backStack)
        // Nothing was lost, so nothing is said to have been.
        assertTrue(messageSink.messageQueue.value.isEmpty())
        leaveImportReport()
        assertTrue(settled.isCompleted)
        assertEquals(0, importCount)
    }

    @Test
    fun `a write outlives a cleared view model and keeps the next one's queue waiting until it ends`() = runTest {
        plannedSkipped = listOf("notes.txt")
        val viewScope = CoroutineScope(backgroundScope.coroutineContext + Job())
        startController(viewScope)
        enqueue()
        settle()
        assertIs<ImportReport.Review>(reportState.importReport.value)
        val gate = CompletableDeferred<Unit>().also { writeGate = it }
        controller.confirmImport()
        settle()
        assertEquals(ImportWorkState.WRITING, work.state.value)
        clearViewModelAndStartAnother(viewScope)
        assertEquals(ImportWorkState.WRITING, work.state.value)
        assertTrue(controller.isImporting.value)
        enqueue()
        settle()
        assertEquals(1, prepareCount)
        gate.complete(Unit)
        settle()
        assertEquals(1, importCount)
        assertEquals(2, prepareCount)
        assertIs<ImportReport.Review>(reportState.importReport.value)
    }

    @Test
    fun `the work reads downloading, unpacking, idle while the review waits, and writing while it is imported`() = runTest {
        download = ImportDownload.File(name = "abcdefghijklmnop.zip", bytes = ZIP_BYTES)
        val states = mutableListOf<ImportWorkState>()
        startController()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { work.state.collect { states += it } }
        controller.importSharedLink(sharedLink)
        settle()
        assertIs<ImportReport.Review>(reportState.importReport.value)
        controller.confirmImport()
        settle()
        assertEquals(1, importCount)
        assertEquals(
            listOf(
                ImportWorkState.IDLE,
                ImportWorkState.DOWNLOADING,
                ImportWorkState.UNPACKING,
                ImportWorkState.IDLE,
                ImportWorkState.WRITING,
                ImportWorkState.IDLE,
            ),
            states,
        )
    }

    @Test
    fun `Cancel leaves the loading screen only once the cancelled preparation has cleaned up after itself`() = runTest {
        prepareGate = CompletableDeferred()
        val cleanupGate = CompletableDeferred<Unit>()
        var isCleanedUp = false
        onPrepareEnded = {
            withContext(NonCancellable) {
                cleanupGate.await()
                isCleanedUp = true
            }
        }
        startController()
        val settled = enqueueTwoSongs()
        settle()
        controller.cancelImportPreparation()
        settle()
        assertIs<ImportReport.Loading>(reportState.importReport.value)
        assertTrue(CampfireDestination.ImportReport in backStack)
        assertFalse(settled.isCompleted)
        cleanupGate.complete(Unit)
        settle()
        assertTrue(isCleanedUp)
        assertNull(reportState.importReport.value)
        assertFalse(CampfireDestination.ImportReport in backStack)
        assertTrue(settled.isCompleted)
        assertEquals(0, importCount)
    }

    @Test
    fun `the work's cancel ends a preparation as the screen's Cancel does`() = runTest {
        prepareGate = CompletableDeferred()
        startController()
        val settled = enqueueTwoSongs()
        settle()
        assertEquals(ImportWorkState.UNPACKING, work.state.value)
        work.cancel()
        settle()
        assertEquals(ImportWorkState.IDLE, work.state.value)
        assertNull(reportState.importReport.value)
        assertFalse(CampfireDestination.ImportReport in backStack)
        assertFalse(controller.isImporting.value)
        assertTrue(settled.isCompleted)
        assertEquals(0, importCount)
        assertTrue(messageSink.messageQueue.value.isEmpty())
    }

    /**
     * Two song files, which the import screen is put up for while they are read (see [isReportedWhileRead]), planned as
     * two songs, which it reviews rather than previews.
     */
    private fun enqueueTwoSongs(): CompletableDeferred<Unit> {
        plannedSongs = listOf(songEntry("a.cho"), songEntry("b.cho"))
        return controller.enqueueImport(
            ImportSource.Files(listOf(ImportedFile(name = "a.cho", bytes = byteArrayOf(1)), ImportedFile(name = "b.cho", bytes = byteArrayOf(2)))),
        )
    }

    private fun enqueuePaste(suggestion: SharedTextTitle? = null) = controller.enqueueImport(
        source = ImportSource.Files(listOf(ImportedFile(name = "Pasted.txt", bytes = "Pasted".encodeToByteArray()))),
        isNewSongFromText = true,
        suggestion = suggestion,
    )
}

private val CampfireDestination.isSongPreview get() = this is CampfireDestination.SongDetails && importPreview?.isReviewPager == false

private val STAGED_PICTURE = StagedFile(name = "0123456789abcdef-1-${"a".repeat(64)}.png", size = 3, sha256Hex = "a".repeat(64))
private val STAGED_ARCHIVE = ImportedFile(
    name = "library.zip",
    bytes = ByteArray(0),
    staged = StagedFile(name = "0123456789abcdef-1-1.zip", size = 10, sha256Hex = null),
)

/** The entry with a picture its archive staged, as a staged archive's plan brings one. */
private fun ImportPlan.SongEntry.withStagedPicture() = copy(
    images = listOf(ImportPlan.SongImage(source = "chart.png", name = "aaaaaaaaaaaaaaaa.png", content = IncomingContent.Staged(STAGED_PICTURE), problem = null)),
)

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

private fun setlistEntry(fileName: String, status: ImportPlan.Status) = ImportPlan.SetlistEntry(
    fileName = fileName,
    setlist = Setlist(
        fileName = fileName,
        title = fileName,
        description = "",
        date = LocalDate(2026, 11, 4),
        isArchived = false,
        entries = listOf(Setlist.Entry(songFileName = "riverlight.cho")),
        size = 0,
    ),
    status = status,
    sourceFileName = fileName,
)

private val ZIP_BYTES = byteArrayOf(0x50, 0x4b, 3, 4, 20, 0)
