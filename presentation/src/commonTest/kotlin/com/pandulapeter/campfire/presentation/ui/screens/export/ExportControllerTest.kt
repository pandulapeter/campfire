/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.api.export.ExportLibraryUseCase
import com.pandulapeter.campfire.domain.api.export.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.api.export.ExportSongsUseCase
import com.pandulapeter.campfire.domain.api.preferences.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveCapo
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveTempo
import com.pandulapeter.campfire.presentation.ui.playing.Transpositions
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import com.pandulapeter.campfire.presentation.ui.state.songText.FakeSongFiles
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportControllerTest {

    private val setlist = Setlist(
        fileName = "gig.setlist.json", title = "Gig", description = "", date = LocalDate(2026, 1, 1), isArchived = false,
        entries = listOf(Setlist.Entry("a.cho"), Setlist.Entry("b.cho")), size = 0L,
    )
    private val setlistExport = DialogType.Export(setlist = setlist)
    private val songsExport = DialogType.Export(songs = listOf(song("a.cho"), song("b.cho"), song("c.cho")))

    /** What the export use cases were asked for, by what they hand out. */
    private val requests = mutableListOf<String>()
    private var libraryExport: ExportLibraryUseCase.Result? = null
    private val picker = RecordingFilePicker()

    @Test
    fun `a setlist's files are saved whole, confirmed, and the screen closes`() = runTest {
        val (controller, dialogHost, messageSink) = controller(visibleDialog = setlistExport)
        controller.exportFiles(picker, setlistExport, isShare = false)
        runCurrent()
        assertEquals(listOf("setlist gig.setlist.json"), requests)
        assertEquals(listOf("gig.zip"), picker.saved.map { it.name })
        assertEquals(listOf<Message>(Message.SetlistExported), messageSink.messages())
        assertNull(dialogHost.visibleDialog.value)
    }

    @Test
    fun `several songs are exported as an archive of them all`() = runTest {
        val (controller, _, messageSink) = controller(visibleDialog = songsExport)
        controller.exportFiles(picker, songsExport, isShare = false)
        runCurrent()
        assertEquals(listOf("songs [a.cho, b.cho, c.cho] archive: true"), requests)
        assertEquals(listOf<Message>(Message.SongsExported), messageSink.messages())
    }

    @Test
    fun `nothing is exported for a screen that is no longer shown`() = runTest {
        val (controller, _, _) = controller(visibleDialog = null)
        controller.exportFiles(picker, setlistExport, isShare = false)
        runCurrent()
        assertTrue(requests.isEmpty())
        assertTrue(picker.saved.isEmpty())
    }

    @Test
    fun `a share is not confirmed and leaves the screen open`() = runTest {
        val (controller, dialogHost, messageSink) = controller(visibleDialog = setlistExport)
        controller.exportFiles(picker, setlistExport, isShare = true)
        runCurrent()
        assertEquals(listOf("gig.zip"), picker.shared.map { it.name })
        assertTrue(messageSink.messages().isEmpty())
        assertEquals(setlistExport, dialogHost.visibleDialog.value)
    }

    @Test
    fun `a dismissed picker says nothing and leaves the screen open`() = runTest {
        picker.isSaved = false
        val (controller, dialogHost, messageSink) = controller(visibleDialog = setlistExport)
        controller.exportFiles(picker, setlistExport, isShare = false)
        runCurrent()
        assertTrue(messageSink.messages().isEmpty())
        assertEquals(setlistExport, dialogHost.visibleDialog.value)
    }

    @Test
    fun `an empty library is an export that failed`() = runTest {
        val (controller, _, messageSink) = controller(visibleDialog = null)
        controller.exportLibrary(picker)
        runCurrent()
        assertTrue(picker.saved.isEmpty())
        assertEquals(listOf<Message>(Message.ExportFailed), messageSink.messages())
    }

    @Test
    fun `a backup missing files says which instead of confirming`() = runTest {
        libraryExport = ExportLibraryUseCase.Result(file = zip("campfire.zip", size = 1), skippedFileNames = listOf("broken.cho"))
        val (controller, _, messageSink) = controller(visibleDialog = null)
        controller.exportLibrary(picker)
        runCurrent()
        assertEquals(listOf<Message>(Message.ExportSkippedFiles(listOf("broken.cho"))), messageSink.messages())
    }

    @Test
    fun `a backup too large to import back is saved with a warning`() = runTest {
        libraryExport = ExportLibraryUseCase.Result(file = zip("campfire.zip", size = ImportLimits.MAX_IMPORT_SIZE.toInt() + 1), skippedFileNames = emptyList())
        val (controller, _, messageSink) = controller(visibleDialog = null)
        controller.exportLibrary(picker)
        runCurrent()
        assertEquals(listOf("campfire.zip"), picker.saved.map { it.name })
        assertEquals(listOf<Message>(Message.ExportTooLargeToImport), messageSink.messages())
    }

    @Test
    fun `a second transfer is ignored while the first one runs`() = runTest {
        val (controller, _, _) = controller(visibleDialog = null)
        val gate = CompletableDeferred<Unit>()
        assertTrue(controller.launchFileTransfer { gate.await() } != null)
        runCurrent()
        assertTrue(controller.isFileTransferActive.value)
        assertNull(controller.launchFileTransfer { })
        gate.complete(Unit)
        runCurrent()
        assertFalse(controller.isFileTransferActive.value)
        assertTrue(controller.launchFileTransfer { } != null)
    }

    private fun MessageSink.messages() = messageQueue.value.map { it.value }

    private fun song(fileName: String) = Song(
        fileName = fileName, title = fileName, artist = "", key = null, transpose = 0,
        tags = emptyList(), languages = emptyList(), coverArtUrl = null, hasChords = false,
        canUpdateFileName = false, lastModified = 0L, size = 0L,
    )

    private fun zip(name: String, size: Int) = ExportedFile(name, ExportedFile.ZIP_MIME_TYPE, ByteArray(size))

    private fun TestScope.controller(visibleDialog: DialogType?): Triple<ExportController, DialogHost, MessageSink> {
        val dialogHost = DialogHost(backgroundScope).apply { setVisibleDialog(visibleDialog) }
        val messageSink = MessageSink(backgroundScope)
        val controller = ExportController(
            scope = backgroundScope,
            dialogHost = dialogHost,
            messageSink = messageSink,
            screenData = MutableStateFlow(DataState.Loading(null)),
            userPreferencesState = MutableStateFlow(DataState.Loading(null)),
            transpositions = MutableStateFlow(Transpositions()),
            effectiveTempoOf = { _, _ -> EffectiveTempo(bpm = 120, source = EffectiveTempo.Source.DEFAULT, songBpm = 120) },
            effectiveCapoOf = { _, _ -> EffectiveCapo(fret = 0, songFret = 0) },
            songRenderer = testSongRenderer(),
            getSongContent = FakeSongFiles(),
            exportSongs = object : ExportSongsUseCase {
                override suspend fun invoke(fileNames: List<String>, isArchive: Boolean, isDated: Boolean): ExportedFile {
                    requests += "songs $fileNames archive: $isArchive"
                    return zip("songs.zip", size = 1)
                }
            },
            exportSetlist = object : ExportSetlistUseCase {
                override suspend fun invoke(setlistFileName: String, isDated: Boolean): ExportedFile {
                    requests += "setlist $setlistFileName"
                    return zip("gig.zip", size = 1)
                }
            },
            exportLibrary = object : ExportLibraryUseCase {
                override suspend fun invoke() = libraryExport
            },
            updateUserPreferences = object : UpdateUserPreferencesUseCase {
                override suspend fun invoke(update: (UserPreferences) -> UserPreferences) = Unit
            },
            writeDelayMillis = 0L,
        )
        return Triple(controller, dialogHost, messageSink)
    }

    private class RecordingFilePicker : FilePicker {
        var isSaved = true
        val saved = mutableListOf<ExportedFile>()
        val shared = mutableListOf<ExportedFile>()

        override suspend fun pickFiles() = emptyList<ImportedFile>()

        override suspend fun saveFile(file: ExportedFile): Boolean {
            if (isSaved) saved += file
            return isSaved
        }

        override suspend fun shareFile(file: ExportedFile): Boolean {
            shared += file
            return true
        }
    }
}
