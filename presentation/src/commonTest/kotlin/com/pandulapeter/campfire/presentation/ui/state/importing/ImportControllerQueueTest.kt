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
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.SharedLink
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.domain.api.library.DeleteLibraryUseCase
import com.pandulapeter.campfire.domain.api.importing.DownloadImportFileUseCase
import com.pandulapeter.campfire.domain.api.importing.ImportFilesUseCase
import com.pandulapeter.campfire.domain.api.importing.PrepareImportUseCase
import com.pandulapeter.campfire.domain.api.library.GetSharedLinkImportUseCase
import com.pandulapeter.campfire.domain.api.library.RememberDemoLibraryFilesUseCase
import com.pandulapeter.campfire.domain.api.library.RememberSharedLinkImportUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.ImportPreview
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextTitle
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportFailure
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
    private val loadedSongTexts = mutableListOf<String>()
    private val newSongTexts = mutableListOf<String>()
    private val newSongSuggestions = mutableListOf<SharedTextTitle?>()
    private val library = MutableStateFlow(SongLookup(songsByFileName = emptyMap(), isLibraryRead = true))
    private var isLibraryIndexedAtOnce = true
    private var areSongsEnabled = true
    private var onPrepare: () -> Unit = {}
    private var plannedSongs = listOf(songEntry("new.cho"))

    /** What the plans leave out, which makes a batch of one song more than that song, and so reviewed rather than previewed. */
    private var plannedSkipped = emptyList<String>()
    private var isImportFailing = false
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
            messageSink = MessageSink(backgroundScope).also { messageSink = it },
            backStack = backStack,
            reportState = reportState,
            openImportedSong = { openedSongs += it },
            loadSongText = { loadedSongTexts += it },
            openNewSongEditor = { text, suggestion ->
                newSongTexts += text
                newSongSuggestions += suggestion
                backStack.removeAll { it is CampfireDestination.SongEditor }
                backStack += CampfireDestination.SongEditor.newSong()
            },
            areSongsEnabled = { areSongsEnabled },
            songLookup = library,
            launchFileTransfer = { null },
            isLeaving = { false },
            downloadImportFile = object : DownloadImportFileUseCase {
                override suspend fun invoke(url: String) = download.also { downloadedUrls += url }
            },
            prepareImport = object : PrepareImportUseCase {
                override suspend fun invoke(files: List<ImportedFile>, onProgress: (ImportProgress) -> Unit): ImportPlan {
                    prepareCount++
                    preparedFiles += files
                    onPrepare()
                    return ImportPlan(songs = nextPlans.removeFirstOrNull() ?: plannedSongs, skippedFileNames = plannedSkipped)
                }
            },
            importFiles = object : ImportFilesUseCase {
                override suspend fun invoke(plan: ImportPlan, choices: ImportChoices, onProgress: (ImportProgress) -> Unit): ImportResult {
                    importCount++
                    if (isImportFailing) error("The disk is full.")
                    // A kept-both conflict lands under a numbered name, as the storage layer writes it.
                    val written = plan.songs.mapIndexed { index, entry ->
                        val isKeptBoth = entry.status == ImportPlan.Status.CONFLICTING &&
                            choices.resolutionOf(ImportEntryKey(ImportEntryKey.Kind.SONG, index)) == ImportConflictResolution.KEEP_BOTH
                        if (isKeptBoth) entry.fileName.replace(".cho", "_2.cho") else entry.fileName
                    }
                    if (isLibraryIndexedAtOnce) library.value = library.value.withSongs(written)
                    return ImportResult(importedSongFileNames = written)
                }
            },
            rememberDemoLibraryFiles = object : RememberDemoLibraryFilesUseCase {
                override suspend fun invoke(songFileNames: Collection<String>, setlistFileNames: Collection<String>) = Unit
            },
            getSharedLinkImport = object : GetSharedLinkImportUseCase {
                override suspend fun invoke(linkFileId: String) = sharedLinkImports[linkFileId].orEmpty()
            },
            rememberSharedLinkImport = object : RememberSharedLinkImportUseCase {
                override suspend fun invoke(linkFileId: String, songFileNames: Collection<String>, setlistFileNames: Collection<String>) {
                    rememberedSharedLinkImports += linkFileId to (songFileNames + setlistFileNames).toList()
                }
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
    fun `a batch arriving while a preview is up waits for it rather than joining it`() = runTest {
        startController()
        handOver()
        settle()
        nextPlans += listOf(songEntry("other.cho"))
        val settled = enqueue()
        settle()
        assertEquals(1, prepareCount)
        assertEquals(listOf("new.cho"), assertIs<ImportReport.Review>(reportState.importReport.value).plan.songs.map { it.fileName })
        backStack.removeAt(backStack.lastIndex)
        controller.onBackStackChanged()
        settle()
        assertFalse(settled.isCompleted)
        assertEquals(listOf("other.cho"), assertIs<ImportReport.Review>(reportState.importReport.value).plan.songs.map { it.fileName })
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
        assertEquals(listOf<Message>(Message.SongAlreadyInLibrary), messageSink.messageQueue.value.map { it.value })
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

    private fun enqueuePaste(suggestion: SharedTextTitle? = null) = controller.enqueueImport(
        source = ImportSource.Files(listOf(ImportedFile(name = "Pasted.txt", bytes = "Pasted".encodeToByteArray()))),
        isNewSongFromText = true,
        suggestion = suggestion,
    )
}

private val CampfireDestination.isSongPreview get() = this is CampfireDestination.SongDetails && importPreview?.isReviewPager == false

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

private val ZIP_BYTES = byteArrayOf(0x50, 0x4b, 3, 4, 20, 0)
