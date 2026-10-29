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

import com.pandulapeter.campfire.presentation.mapper.toPreferFlats
import com.pandulapeter.campfire.presentation.ui.notation.NotationChordSpelling
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.domain.api.export.ExportLibraryUseCase
import com.pandulapeter.campfire.domain.api.export.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.api.export.ExportSongsUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.preferences.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.CAMPFIRE_VERSION_NAME
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.fontScale.FontScaleController
import com.pandulapeter.campfire.presentation.ui.instrument.storedChordShapes
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.platform.DEVELOPER_EMAIL
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.platform.PROCESS_LOG_FILE_NAME
import com.pandulapeter.campfire.presentation.ui.platform.appBuild
import com.pandulapeter.campfire.presentation.ui.platform.canShareProcessLog
import com.pandulapeter.campfire.presentation.ui.platform.readProcessLog
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveCapo
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveTempo
import com.pandulapeter.campfire.presentation.ui.playing.SongPlayback
import com.pandulapeter.campfire.presentation.ui.playing.Transpositions
import com.pandulapeter.campfire.presentation.ui.playing.playsWithoutCapo
import com.pandulapeter.campfire.presentation.ui.playing.shapeCapoFor
import com.pandulapeter.campfire.presentation.ui.playing.soundingTransposition
import com.pandulapeter.campfire.presentation.ui.playing.tabCapoFor
import com.pandulapeter.campfire.presentation.ui.playing.withCapo
import com.pandulapeter.campfire.presentation.ui.playing.withoutCapo
import com.pandulapeter.campfire.presentation.ui.playing.withTempo
import com.pandulapeter.campfire.presentation.ui.songView.model.inferredKeyOfText
import com.pandulapeter.campfire.presentation.ui.print.PdfExportProgress
import com.pandulapeter.campfire.presentation.ui.print.PrintSong
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.print.printChordsOf
import com.pandulapeter.campfire.presentation.ui.print.printScaleAnalysisOf
import com.pandulapeter.campfire.presentation.ui.print.printScaleGroupsOf
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.state.DebouncedPreference
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.tabs.TabNoticeLabels
import com.pandulapeter.campfire.presentation.ui.tabs.tabReadingOf
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

    suspend fun preparePrintSource(dialog: DialogType.Export, tabNoticeLabels: TabNoticeLabels, sectionLabels: DefaultSectionLabels): PrintSource {
        val preferences = userPreferencesState.value.data
        val setlist = dialog.setlist
        val entries = when {
            setlist != null -> setlist.entries
            dialog.songs.isNotEmpty() -> dialog.songs.map { Setlist.Entry(it.fileName) }
            else -> listOf(Setlist.Entry(requireNotNull(dialog.song).fileName))
        }
        val songs = screenData.value.data?.unfilteredSongs.orEmpty().associateBy { it.fileName }
        // Read the way the viewer reads it, so that the page is in the key the screen shows: wrapped, and for a song a
        // setlist names more than once, the one amount the viewer settles on rather than whichever entry comes first.
        val setlistFileName = setlist?.fileName ?: dialog.songSetlistFileName
        val spelling = preferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
        // Collected whether or not the export asks for the diagrams, so that ticking them lays the pages out again rather
        // than reading the songs again; with the feature switched off the export does not offer them at all.
        val chordTuning = preferences?.takeIf { it.areChordsEnabled && it.areChordDiagramsEnabled }?.chordTuning()
        val storedChordShapes = chordTuning?.let { preferences.storedChordShapes(it) }.orEmpty()
        val playsWithoutCapo = preferences.playsWithoutCapo
        // Read whether or not the export asks for them, as the chords are, and only where the song details screen suggests
        // them, which is also where the export offers them.
        val suggestsScales = FeatureSwitches.of(preferences).areScaleSuggestionsEnabled && preferences?.areChordsEnabled != false
        // The diagrams name their degrees in print wherever they do on the screen.
        val showsDegrees = preferences?.areChordDegreesEnabled == true
        // The tabs are printed for the reader as the screen shows them: theirs, moved to their strings.
        val tabs = preferences?.let { tabReadingOf(preferences = it, labels = tabNoticeLabels, isMoved = true) }
        val printSongs = entries.mapIndexed { index, entry ->
            val song = songs[entry.songFileName] ?: dialog.songs.firstOrNull { it.fileName == entry.songFileName } ?: dialog.song
            val content = getSongContent(entry.songFileName)
            val playedTransposition = transpositions.value[entry.songFileName, setlistFileName]
            // Tempo and capo are printed as the screen shows them, as the key is: the setlist's, else the reader's own,
            // else the file's.
            val effectiveTempo = effectiveTempoOf(entry.songFileName, setlistFileName)
            val tempo = effectiveTempo.takeUnless { it.isDefault }?.bpm
            val effectiveCapo = effectiveCapoOf(entry.songFileName, setlistFileName)
            val capo = effectiveCapo.takeUnless { it.isDefault }?.fret
            val playback = SongPlayback(transposition = playedTransposition, capo = effectiveCapo, tempo = effectiveTempo)
            // The pages are in the chords that sound, as the screen is, the file's capo counted in; the diagrams are the
            // shapes played above the capo the page prints, none on the keyboard, which prints no capo either.
            val transposition = playback.soundingTransposition
            val shapeCapo = playback.shapeCapoFor(preferences)
            // The scales and the degrees read the file's own capo, which says how its key is meant, as on the screen.
            val transposed = content?.let { withContext(Dispatchers.Default) { songRenderer.transposedSong(it.text, transposition, spelling, tabs = tabs, capo = playback.tabCapoFor(preferences)) } }
            val printed = transposed?.withTempo(tempo)?.let { if (playsWithoutCapo) it.withoutCapo() else it.withCapo(capo) }
            // A file that names no key is named by the one its chords are in, as on the screen; that key is already in the
            // reader's notation, so it is set after the notation rather than before.
            val inferred = if (song != null && song.key.isNullOrBlank() && content != null) {
                withContext(Dispatchers.Default) { songRenderer.inferredKeyOfText(content.text) }
            } else {
                null
            }
            val inferredKey = if (song != null && inferred != null) songRenderer.renderKey(song = song, transposition = transposition, spelling = spelling, inferredKey = inferred) else null
            // A numbering counts the chords of a song naming no key from that key, in the frame of the printed chords.
            val inferredChordsKey = inferred?.let { songRenderer.movedKey(inferredKey = it, transposition = transposition, spelling = spelling) }
            val rendered = printed?.let { withContext(Dispatchers.Default) { songRenderer.notatedSong(it, spelling, inferredChordsKey) } }?.let { rendered ->
                inferredKey?.takeIf { rendered.metadata.key.isNullOrBlank() }?.let { rendered.copy(metadata = rendered.metadata.copy(key = it)) } ?: rendered
            }
            // One walk answers the scales, the keys the chords are grouped by, and the degrees, as on the screen.
            val analysis = if (transposed != null && (suggestsScales || chordTuning != null)) {
                withContext(Dispatchers.Default) { printScaleAnalysisOf(transposed, sectionLabels) }
            } else {
                null
            }
            val chords = if (transposed != null && chordTuning != null) {
                withContext(Dispatchers.Default) {
                    printChordsOf(transposed, spelling.notation.toChordNotation(), chordTuning, storedChordShapes, showsDegrees, analysis, shapeCapo, inferredChordsKey)
                }
            } else {
                emptyList()
            }
            val scaleGroups = if (transposed != null && suggestsScales) {
                withContext(Dispatchers.Default) { printScaleGroupsOf(transposed, sectionLabels, spelling.notation.toChordNotation(), analysis, inferredChordsKey) }
            } else {
                emptyList()
            }
            PrintSong(entry.songFileName, song?.title ?: entry.songFileName.substringBeforeLast('.'), song?.artist,
                index = if (setlist == null) null else index + 1, transposition = playedTransposition, song = rendered, text = content?.text, chords = chords,
                scaleGroups = scaleGroups,
                notationChords = NotationChordSpelling(
                    semitones = (transposed?.metadata?.transpose ?: 0) + transposition,
                    preferFlats = spelling.accidentals.toPreferFlats(),
                    notation = spelling.notation.toChordNotation(),
                ))
        }
        // Several songs are titled by the screen, which counts them in the app's language.
        return PrintSource(
            title = setlist?.title ?: dialog.song?.title.orEmpty(),
            description = setlist?.description.orEmpty(),
            kind = when {
                setlist != null -> PrintSource.Kind.SETLIST
                dialog.songs.isNotEmpty() -> PrintSource.Kind.SONGS
                else -> PrintSource.Kind.SONG
            },
            songs = printSongs,
        )
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
                    bytes?.let { ExportedFile(fileName, ExportedFile.PDF_MIME_TYPE, it) }
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
     * the songs it names, and several songs as a zip of those songs alone. A saved file closes the screen, as a saved PDF
     * does, and the same guard keeps a tap that lands while the screen slides away from bringing a picker up over
     * whatever is under it.
     */
    fun exportFiles(filePicker: FilePicker, dialog: DialogType.Export, isShare: Boolean) {
        if (dialogHost.visibleDialog.value != dialog) return
        val setlist = dialog.setlist
        launchFileTransfer {
            save(
                filePicker = filePicker,
                savedMessage = when {
                    setlist != null -> Message.SetlistExported
                    dialog.songs.isNotEmpty() -> Message.SongsExported
                    else -> Message.SongExported
                },
                isShare = isShare,
                // Building a setlist's archive reads every song it names, which can outlast a Back on a slow disk; a file
                // finished for a screen that has gone would bring its picker up over whatever is on screen by then.
                isWanted = { dialogHost.visibleDialog.value == dialog },
                onSaved = { dialogHost.dismissSheet(dialog) },
            ) {
                when {
                    setlist != null -> exportSetlist(setlistFileName = setlist.fileName, isDated = true)
                    dialog.songs.isNotEmpty() -> exportSongs(fileNames = dialog.songs.map { it.fileName }, isArchive = true, isDated = true)
                    else -> exportSongs(fileNames = listOf(requireNotNull(dialog.song).fileName), isArchive = false, isDated = true)
                }
            }
        }
    }

    /**
     * A link's QR code, saved as a picture, which leaves the screen open: the link may still be copied or shared. [png]
     * draws it off the main thread, where a large symbol's picture is a moment's work.
     */
    fun exportQrCode(filePicker: FilePicker, dialog: DialogType.Export, fileName: String, png: suspend () -> ByteArray) {
        if (dialogHost.visibleDialog.value != dialog) return
        launchFileTransfer {
            save(filePicker = filePicker, savedMessage = Message.QrCodeSaved) {
                ExportedFile(fileName, ExportedFile.PNG_MIME_TYPE, withContext(Dispatchers.Default) { png() })
            }
        }
    }

    /** Hands a link to the platform's share sheet, while the export screen it was made on is still open. */
    fun shareLink(filePicker: FilePicker, dialog: DialogType.Export, link: String, subject: String) {
        if (dialogHost.visibleDialog.value != dialog) return
        shareLink(filePicker = filePicker, link = link, subject = subject)
    }

    /** Hands a link to the platform's share sheet, as one transfer, since the sheet cannot be up twice either. */
    fun shareLink(filePicker: FilePicker, link: String, subject: String) {
        launchFileTransfer {
            try {
                filePicker.shareText(text = link, subject = subject)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                println("Could not share the link: ${exception.message}")
                messageSink.sendMessage(Message.ExportFailed)
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

    /**
     * Attaches the process log ([readProcessLog]) to a new mail to the developer where the platform can, and otherwise
     * hands it to the share sheet, or to a save dialog where there is none either. It goes under a first line naming the
     * version and the build, which the log itself says nothing about.
     */
    fun sendProcessLog(filePicker: FilePicker) = launchFileTransfer {
        val file = withContext(Dispatchers.Default) { readProcessLog() }?.let { log ->
            ExportedFile(
                name = PROCESS_LOG_FILE_NAME,
                mimeType = ExportedFile.TEXT_MIME_TYPE,
                bytes = "Campfire $CAMPFIRE_VERSION_NAME (${appBuild.name.lowercase()})\n\n$log".encodeToByteArray(),
            )
        }
        val isEmailed = file != null && filePicker.canEmailFiles && try {
            filePicker.emailFile(file = file, to = DEVELOPER_EMAIL, subject = "Campfire $CAMPFIRE_VERSION_NAME log")
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not email the log: ${exception.message}")
            false
        }
        if (!isEmailed) save(filePicker = filePicker, savedMessage = Message.ProcessLogSaved, isShare = filePicker.canShareProcessLog) { file }
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
     *
     * A file [isWanted] no longer asks for once it is made is dropped without a word: whoever asked for it has gone.
     */
    private suspend fun save(
        filePicker: FilePicker,
        savedMessage: Message,
        isShare: Boolean = false,
        isWanted: () -> Boolean = { true },
        warnings: () -> List<Message> = { emptyList() },
        onSaved: () -> Unit = {},
        export: suspend () -> ExportedFile?,
    ) = try {
        val file = export()
        println("Export: ${file?.let { "${it.mimeType}, ${it.bytes.size} bytes, ${if (isShare) "shared" else "saved"}" } ?: "nothing to export"}")
        when {
            !isWanted() -> Unit
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
