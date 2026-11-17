/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songText

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProAttachments
import com.pandulapeter.campfire.chordpro.model.ChordProAttachment
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFileSizes
import com.pandulapeter.campfire.domain.api.export.ExportAttachmentUseCase
import com.pandulapeter.campfire.domain.api.images.HoldPickedAttachmentsUseCase
import com.pandulapeter.campfire.domain.api.images.SaveSongAttachmentsUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.pictures.AttachmentIntake
import com.pandulapeter.campfire.presentation.ui.pictures.PictureWork
import com.pandulapeter.campfire.presentation.ui.platform.FileOpener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The files attached to songs, as the About the song sheet and its Attachments sheet see them: which of them this device
 * holds ([fileSizes]), picking one more for the sheet ([pick], answered through [picked]), writing the sheet's list
 * ([save]), and opening one ([open]).
 *
 * @param songMetadataEditing Where the text a sheet edits is read and the editor's draft is edited.
 * @param openDialogs The dialogs open, which say whether an Attachments sheet is, see [heldPicks].
 */
internal class SongAttachmentEditing(
    private val scope: CoroutineScope,
    private val messageSink: MessageSink,
    private val songTextStore: SongTextStore,
    private val songMetadataEditing: SongMetadataEditing,
    private val intake: AttachmentIntake,
    private val work: PictureWork,
    private val saveSongAttachments: SaveSongAttachmentsUseCase,
    private val exportAttachment: ExportAttachmentUseCase,
    private val holdPickedAttachments: HoldPickedAttachmentsUseCase,
    librarySizes: StateFlow<LibraryFileSizes?>,
    openDialogs: StateFlow<List<DialogType>>,
) {

    /**
     * What Add file kept on the draft shelf while an Attachments sheet was open, held from the shelf's tidying
     * ([holdPickedAttachments]) until no sheet is open and no Save of one is underway: an editor closing elsewhere empties
     * the shelf, and a Save after it would name a file that is not there. Touched on [scope]'s thread only, like
     * [isSheetOpen] and [savesUnderway]; [holding] keeps the reports in the order they were made.
     */
    private var heldPicks = emptySet<String>()
    private var reportedPicks = emptySet<String>()
    private var isSheetOpen = false
    private var savesUnderway = 0
    private val holding = Mutex()

    init {
        scope.launch {
            openDialogs.map { dialogs -> dialogs.any { it is DialogType.SongAttachments } }.distinctUntilChanged().collect { isOpen ->
                isSheetOpen = isOpen
                reportHeldPicks()
            }
        }
    }

    /** The size of every file this device holds for a song, by name, the draft shelf's included; see `LibraryFileSizes`. */
    val fileSizes: StateFlow<Map<String, Long>> = librarySizes
        .map { it?.fileSizes.orEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, librarySizes.value?.fileSizes.orEmpty())

    /**
     * Files the sheet's Add file kept on the draft shelf, each for the sheet of its target to add as a row. A channel, so
     * that one arriving while the sheet is composed again (the picker answering across a rotation) waits for it.
     */
    private val _picked = Channel<PickedAttachment>(Channel.BUFFERED)
    val picked = _picked.receiveAsFlow()

    /**
     * The Attachments sheet's Add file: [pickAttachment] asks for a file, which is kept on the draft shelf (refused with a
     * snackbar as the editor's Attach refuses it) and handed to the sheet of [target]. Runs in [scope], so the picker may
     * outlive the sheet. Nothing reaches the library or the song before [save].
     */
    fun pick(target: SongEditTarget, pickAttachment: suspend () -> ImportedFile?) {
        scope.launch {
            val file = pickAttachment() ?: return@launch
            val attachment = intake.keep(file) ?: return@launch
            if (isSheetOpen) {
                heldPicks += attachment.source
                reportHeldPicks()
            }
            _picked.send(PickedAttachment(target = target, attachment = attachment))
        }
    }

    /**
     * Makes [attachments] the song's, from the Attachments sheet, keeping the ones synced in while it was open that it
     * never offered ([offered] being what it opened with), as links are kept. Over the editor the draft's text changes,
     * which Save moves its files in with; otherwise the files picked meanwhile are moved into the library and the file
     * written in one step (`SaveSongAttachmentsUseCase`), under the store's write lock.
     */
    fun save(target: SongEditTarget, attachments: List<ChordProAttachment>, offered: List<ChordProAttachment>) {
        val offeredSources = offered.mapTo(mutableSetOf()) { it.source }
        fun withAddedElsewhere(text: String) = attachments + ChordProParser.parseMetadata(text).attachments.filterNot { it.source in offeredSources }
        when (target) {
            is SongEditTarget.EditorDraft -> songMetadataEditing.editSong(target) { text ->
                ChordProAttachments.setAttachments(text = text, attachments = withAddedElsewhere(text))
            }
            is SongEditTarget.File -> {
                // Counted before the sheet closes, so that its picks stay held until the files are moved into the library.
                savesUnderway++
                messageSink.launchLibraryChange("set a song's attachments") {
                    try {
                        // Tracked, since moving a large file into the library takes a while: the work bar says so.
                        work.track {
                            songTextStore.writeAttachments(target.fileName) { text ->
                                saveSongAttachments(fileName = target.fileName, expectedText = text, attachments = withAddedElsewhere(text))
                            }
                        }
                    } finally {
                        savesUnderway--
                        reportHeldPicks()
                    }
                }
            }
        }
    }

    private suspend fun reportHeldPicks() {
        if (!isSheetOpen && savesUnderway == 0) heldPicks = emptySet()
        val names = heldPicks
        holding.withLock {
            if (names != reportedPicks) {
                holdPickedAttachments(names)
                reportedPicks = names
            }
        }
    }

    /**
     * Opens [attachment] with the app the system picks ([opener]), through a copy under [name], the name the row shows
     * it by. One that is gone meanwhile, or that nothing opens, is said in a snackbar.
     */
    fun open(attachment: ChordProAttachment, name: String, opener: FileOpener) {
        scope.launch {
            try {
                val file = exportAttachment(source = attachment.source, name = name)
                if (file == null) {
                    messageSink.sendMessage(Message.OperationFailed)
                } else if (!opener.open(file)) {
                    messageSink.sendMessage(Message.AttachmentNotOpened)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                println("Could not open an attachment: ${exception.message}")
                messageSink.sendMessage(Message.OperationFailed)
            }
        }
    }
}

/** A file the Attachments sheet of [target] picked and the draft shelf kept, see [SongAttachmentEditing.picked]. */
internal class PickedAttachment(val target: SongEditTarget, val attachment: ChordProAttachment)
