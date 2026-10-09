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
import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.useCases.ExportLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportSongsUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.ui.chords.toChordInstrument
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.fontScale.FontScaleController
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveCapo
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveTempo
import com.pandulapeter.campfire.presentation.ui.playing.Transpositions
import com.pandulapeter.campfire.presentation.ui.playing.withCapo
import com.pandulapeter.campfire.presentation.ui.playing.withTempo
import com.pandulapeter.campfire.presentation.ui.print.PdfExportProgress
import com.pandulapeter.campfire.presentation.ui.print.PrintSong
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.print.printChordsOf
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.state.DebouncedPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The export screen and every file the app hands out: the PDF of a song or a setlist, their own files, a backup of the
 * whole library, and the one transfer - an export or an import's picker - that may run at a time.
 *
 * @param effectiveTempoOf The tempo a song plays at where it is opened, see `PlayingOverrides.effectiveTempoOf`.
 * @param effectiveCapoOf The fret a song is capoed at where it is opened, see `PlayingOverrides.effectiveCapoOf`.
 * @param writeDelayMillis How long the export options have to hold still before they are written.
 */
internal class ExportController(
    private val scope: CoroutineScope,
    private val dialogHost: DialogHost,
    private val messageSink: MessageSink,
    private val screenData: StateFlow<DataState<ScreenData>>,
    private val userPreferencesState: StateFlow<DataState<UserPreferences>>,
    private val transpositions: StateFlow<Transpositions>,
    private val effectiveTempoOf: (songFileName: String, setlistFileName: String?) -> EffectiveTempo,
    private val effectiveCapoOf: (songFileName: String, setlistFileName: String?) -> EffectiveCapo,
    private val songRenderer: SongRenderer,
    private val getSongContent: GetSongContentUseCase,
    private val exportSongs: ExportSongsUseCase,
    private val exportSetlist: ExportSetlistUseCase,
    private val exportLibrary: ExportLibraryUseCase,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,
    private val writeDelayMillis: Long,
) {

    /**
     * The export screen's options as it last set them and not saved yet. A step of its size or its margins is a new
     * value, and saving each one would publish the preferences to every screen once a step, so they are saved the way
     * [FontScaleController.fontScalePreference] is: once they have held still, or at once when the screen goes (see [DialogHost.setVisibleDialog]). A
     * screen composed again within that moment, as a rotation does, starts from this rather than a step back.
     */
    val printSettingsPreference = DebouncedPreference<PrintSettings> { copy(printSettings = it) }

    val pendingPrintSettings = printSettingsPreference.pending

    /**
     * The pick, export or share that is running, from the tap until its picker has answered. A second one is ignored
     * rather than queued: a double tap on a row is one request, the platforms cannot show two pickers at once (Android
     * stacks them and routes the second answer to nobody, UIKit refuses to present over its own, the desktop nests two
     * modal dialogs), and an export builds its whole archive before its picker shows, which is seconds in which the
     * row looks as if it had not been tapped.
     */
    private var fileTransferJob: Job? = null

    /** Whether [fileTransferJob] is running, for the buttons that would start one and so would be ignored meanwhile. */
    private val _isFileTransferActive = MutableStateFlow(false)
    val isFileTransferActive = _isFileTransferActive.asStateFlow()

    /**
     * How far the export screen's PDF has been drawn, from its Save until the file is ready to be handed to the picker,
     * and null otherwise: once the picker is up there is nothing left to count, and nothing to cancel either (see
     * [cancelPdfExport]).
     */
    private val _pdfExportProgress = MutableStateFlow<PdfExportProgress?>(null)
    val pdfExportProgress = _pdfExportProgress.asStateFlow()

    private var pdfExportJob: Job? = null

    /**
     * Only as safe as the pickers are: every one of them has to answer on every way its screen can go away, since a
     * transfer that never ended would keep the app from importing or exporting anything again. Nothing that suspends
     * may come between the tap and the picker either, because the web's file input needs the tap's user activation.
     *
     * [isFileTransferActive] is cleared by the completion of the job that set it, which also comes for a job cancelled
     * before it started, where a `finally` would not run, and only while that job is still the latest: the handler of
     * one that ended late must not clear the flag of the next. The job starts once it is recorded, since the main
     * dispatcher is immediate and a block that never suspends would otherwise complete before it is.
     */
    fun launchFileTransfer(block: suspend () -> Unit): Job? {
        if (fileTransferJob?.isActive == true) return null
        _isFileTransferActive.value = true
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        fileTransferJob = job
        job.invokeOnCompletion { if (fileTransferJob === job) _isFileTransferActive.value = false }
        job.start()
        return job
    }

    suspend fun preparePrintSource(dialog: DialogType.Export): PrintSource {
        val preferences = userPreferencesState.value.data
        val setlist = dialog.setlist
        val entries = setlist?.entries ?: listOf(Setlist.Entry(requireNotNull(dialog.song).fileName))
        val songs = screenData.value.data?.unfilteredSongs.orEmpty().associateBy { it.fileName }
        // Read the way the viewer reads it, so that the page is in the key the screen shows: wrapped, and for a song a
        // setlist names more than once, the one amount the viewer settles on rather than whichever entry comes first.
        val setlistFileName = setlist?.fileName ?: dialog.songSetlistFileName
        val spelling = preferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
        // Collected whether or not the export asks for the diagrams, so that ticking them lays the pages out again rather
        // than reading the songs again; with the feature switched off the export does not offer them at all.
        val chordInstrument = preferences?.takeIf { it.areChordsEnabled && it.areChordDiagramsEnabled }?.chordInstrument?.toChordInstrument()
        val storedChordShapes = chordInstrument?.let { preferences.chordVoicings[it.id] }.orEmpty()
        val printSongs = entries.mapIndexed { index, entry ->
            val song = songs[entry.songFileName] ?: dialog.song
            val content = getSongContent(entry.songFileName)
            val transposition = transpositions.value[entry.songFileName, setlistFileName]
            // A setlist's tempo and capo are printed as its key is, being how the band plays it; the library's
            // overrides are one reader's, like the folded sections, so a song exported from the library prints its
            // file's own.
            val tempo = setlistFileName?.let { effectiveTempoOf(entry.songFileName, it).takeUnless { tempo -> tempo.isDefault }?.bpm }
            val capo = setlistFileName?.let { effectiveCapoOf(entry.songFileName, it).takeUnless { capo -> capo.isDefault }?.fret }
            val transposed = content?.let { withContext(Dispatchers.Default) {
                songRenderer.transposedSong(it.text, transposition, spelling).withTempo(tempo).withCapo(capo)
            } }
            val rendered = transposed?.let { withContext(Dispatchers.Default) { songRenderer.notatedSong(it, spelling) } }
            val chords = if (transposed != null && chordInstrument != null) {
                withContext(Dispatchers.Default) { printChordsOf(transposed, spelling.notation.toChordNotation(), chordInstrument, storedChordShapes) }
            } else {
                emptyList()
            }
            PrintSong(entry.songFileName, song?.title ?: entry.songFileName.substringBeforeLast('.'), song?.artist,
                index = if (setlist == null) null else index + 1, transposition = transposition, song = rendered, text = content?.text, chords = chords)
        }
        return PrintSource(title = setlist?.title ?: requireNotNull(dialog.song).title,
            description = setlist?.description.orEmpty(), isSetlist = setlist != null, songs = printSongs)
    }

    fun setPrintSettings(value: PrintSettings) = printSettingsPreference.set(value.normalized())

    /**
     * [create] draws the pages and reports each one drawn to the callback it is given. Whatever it throws, an
     * `OutOfMemoryError` included, is the export failing rather than the app: a large setlist on a phone with a small
     * heap can run out while drawing, which is reported the way any failed export is. On the web running out of memory
     * is a trap that no handler sees, so there it still ends the app.
     */
    fun exportPdf(
        filePicker: FilePicker,
        fileName: String,
        dialog: DialogType.Export,
        pageCount: Int,
        isShare: Boolean,
        create: suspend (onPage: (done: Int) -> Unit) -> ByteArray,
    ) {
        // A Save kept until the pages were laid out can arrive after the screen was closed, and its picker would come up
        // over whatever is on screen by then. Equality rather than identity: the same export closed and opened again
        // while it slides away is an equal instance, and the screen still showing the old one is that export.
        if (dialogHost.visibleDialog.value != dialog) return
        val job = launchFileTransfer {
            _pdfExportProgress.value = PdfExportProgress(done = 0, total = pageCount)
            try {
                // A share leaves the screen open, since a second share or a save may follow; save() only calls onSaved for a save.
                save(
                    filePicker = filePicker,
                    savedMessage = Message.PdfSaved,
                    isShare = isShare,
                    // Closed from here rather than by the screen hearing of it, which may not be composed at that moment (an
                    // Activity recreated under the picker): dismissSheet does nothing once another dialog took its place.
                    onSaved = { dialogHost.dismissSheet(dialog) },
                ) {
                    val bytes = try {
                        withContext(Dispatchers.Default) { create { done -> _pdfExportProgress.value = PdfExportProgress(done = done, total = pageCount) } }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (throwable: Throwable) {
                        println("Could not create the PDF: ${throwable::class.simpleName}")
                        null
                    } finally {
                        // Before the picker, which cannot be cancelled from here: Android's would stay up and write the
                        // file all the same once it answered.
                        _pdfExportProgress.value = null
                    }
                    bytes?.let { ExportedFile(fileName, "application/pdf", it) }
                }
            } finally {
                _pdfExportProgress.value = null
            }
        }
        if (job != null) pdfExportJob = job
    }

    /** Stops an export that is still drawing its pages, and leaves one that has its picker up to finish. */
    fun cancelPdfExport() {
        if (_pdfExportProgress.value != null) pdfExportJob?.cancel()
    }

    /**
     * The export screen's other format: a song as the `.cho` file it already is, a setlist as a zip of its manifest and
     * the songs it names, narrowed to [songFileNames] where some were left out (null being all of them). A saved file
     * closes the screen, as a saved PDF does, and the same guard keeps a tap that lands while the screen slides away from
     * bringing a picker up over whatever is under it.
     */
    fun exportFiles(filePicker: FilePicker, dialog: DialogType.Export, songFileNames: Set<String>?, isShare: Boolean) {
        if (dialogHost.visibleDialog.value != dialog) return
        val setlist = dialog.setlist
        launchFileTransfer {
            save(
                filePicker = filePicker,
                savedMessage = if (setlist == null) Message.SongExported else Message.SetlistExported,
                isShare = isShare,
                onSaved = { dialogHost.dismissSheet(dialog) },
            ) {
                if (setlist == null) {
                    exportSongs(listOf(requireNotNull(dialog.song).fileName))
                } else {
                    exportSetlist.invoke(setlist.fileName, songFileNames)
                }
            }
        }
    }

    fun exportLibrary(filePicker: FilePicker) = launchFileTransfer {
        var skippedFileNames = emptyList<String>()
        save(
            filePicker = filePicker,
            savedMessage = Message.LibraryExported,
            // After the save rather than instead of it: the archive is a real copy of everything that could be read,
            // and what it is missing is the one thing the user could not otherwise find out.
            warnings = { listOfNotNull(skippedFileNames.takeIf { it.isNotEmpty() }?.let(Message::ExportSkippedFiles)) },
        ) {
            exportLibrary.invoke()?.also { skippedFileNames = it.skippedFileNames }?.file
        }
    }

    /** For the Android shell, whose picker can finish an export long after the coroutine that asked for it is gone. */
    fun onExportFailed() {
        messageSink.sendMessage(Message.ExportFailed)
    }

    /** For the shells, which are what opens a link and so what finds out that nothing did. */
    fun onLinkNotOpened(url: String) {
        messageSink.sendMessage(Message.LinkNotOpened(url))
    }

    /**
     * Nothing to export and a picker that threw are the same thing to the user: the file did not come out.
     *
     * An archive over what an import takes is saved all the same, since it is still a complete copy that unzips by
     * hand, but the user is told so the day it is made rather than the day it is needed. [onSaved] runs once the file
     * has been saved, and not for one the user dismissed the dialog of.
     *
     * A saved file is confirmed with [savedMessage], unless something about it is worth saying instead ([warnings],
     * read once the file has been made): each of those already says the file was saved, and a confirmation queued
     * after them would only hold up the one line that matters. A share is not confirmed at all, since the platform's
     * own sheet is what the user sees it go out through, and it cannot tell a share that happened from one dismissed.
     */
    private suspend fun save(
        filePicker: FilePicker,
        savedMessage: Message,
        isShare: Boolean = false,
        warnings: () -> List<Message> = { emptyList() },
        onSaved: () -> Unit = {},
        export: suspend () -> ExportedFile?,
    ) = try {
        val file = export()
        when {
            file == null -> messageSink.sendMessage(Message.ExportFailed)
            isShare -> filePicker.shareFile(file)
            filePicker.saveFile(file) -> {
                val isTooLargeToImport = file.mimeType == ExportedFile.ZIP_MIME_TYPE && file.bytes.size > ImportLimits.MAX_IMPORT_SIZE
                val messages = listOfNotNull(Message.ExportTooLargeToImport.takeIf { isTooLargeToImport }) + warnings()
                messages.ifEmpty { listOf(savedMessage) }.forEach(messageSink::sendMessage)
                onSaved()
            }
        }
        Unit
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not export: ${exception.message}")
        messageSink.sendMessage(Message.ExportFailed)
    }

    /** Writes the export options once they have held still, see [printSettingsPreference]. */
    fun startPrintSettingsWriter() = printSettingsPreference.start(scope, writeDelayMillis, updateUserPreferences::invoke)

    /** Writes the export options at once, for the export screen going. */
    fun flushPrintSettings() {
        scope.launch { printSettingsPreference.flush(updateUserPreferences::invoke) }
    }
}
