/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.editor

import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.domain.api.images.AddDraftImageUseCase
import com.pandulapeter.campfire.domain.api.images.ClearDraftImagesUseCase
import com.pandulapeter.campfire.domain.api.images.HoldEditorImagesUseCase
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.pictures.PictureFit
import com.pandulapeter.campfire.presentation.ui.pictures.PictureFitter
import com.pandulapeter.campfire.presentation.ui.pictures.PictureLinkDownloader
import com.pandulapeter.campfire.presentation.ui.pictures.imageNamesOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The pictures the editor's text names that are not in the library yet: kept as draft pictures (outside the library,
 * where nothing but Save moves them in, see `SaveWithDraftImagesUseCase`) as they are picked, held from the library's
 * pruning while the text names them, and let go of once no editor is left.
 *
 * Keeping a picture and letting go of them all take turns ([mutex]), and a pick that the editor closed under is dropped
 * whole ([generation]): a picture kept for an editor that is gone would be inserted into the next one, or deleted
 * under the editor it was meant for.
 *
 * @param pictureFitter What a picture is made to fit with before it is kept.
 * @param linkDownloader What a picture asked for by its address is downloaded with, see [downloadImage].
 * @param computation Where the names a text holds are read, away from the main thread: a long song is read on every
 *   pause in the typing.
 */
internal class EditorImages(
    private val scope: CoroutineScope,
    private val messageSink: MessageSink,
    private val addDraftImage: AddDraftImageUseCase,
    private val pictureFitter: PictureFitter,
    private val linkDownloader: PictureLinkDownloader,
    private val holdEditorImages: HoldEditorImagesUseCase,
    private val clearDraftImages: ClearDraftImagesUseCase,
    private val computation: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var generation = 0

    /**
     * Pictures kept for an open editor, for it to insert at its caret through its field's own editing (one undo step). A
     * channel rather than a shared flow, so that an insertion arriving while the editor is being composed again (the
     * picker answering across a rotation) waits for it rather than being lost.
     */
    private val _insertions = Channel<EditorImageInsertion>(Channel.BUFFERED)
    val insertions = _insertions.receiveAsFlow()

    /**
     * Holds from the library's pruning what the editor's text names, a beat after the typing pauses: [texts] is the open
     * editor's text, null once none is open. Started only once the draft a previous run left has been reopened, since
     * until then the repository holds what that draft names on its own, and an empty set reported meanwhile would let go
     * of it.
     */
    @OptIn(FlowPreview::class)
    fun followEditorText(texts: Flow<String?>) = scope.launch {
        texts.distinctUntilChanged()
            .debounce(HOLD_DELAY_MILLIS)
            .map { text -> if (text == null) emptySet() else withContext(computation) { imageNamesOf(text) } }
            .distinctUntilChanged()
            .collect { holdEditorImages(it) }
    }

    /**
     * The Image button: [pickImages] asks the user for pictures, each kept as a draft picture and inserted into the editor
     * of [fileName], in the order picked; one refused is said why. Runs in [scope], so the picker may outlive the screen
     * that opened it.
     */
    fun pickImages(fileName: String, pickImages: suspend () -> List<ImportedFile>) {
        scope.launch {
            val pickedFor = generation
            val files = pickImages()
            if (files.isEmpty()) return@launch
            val names = keep(files) { generation == pickedFor }
            if (names.isNotEmpty()) _insertions.send(EditorImageInsertion(fileName = fileName, names = names))
        }
    }

    /**
     * The Image menu's From a link: the picture at [url], downloaded and then kept and inserted into the editor of
     * [fileName] as a picked one is; one that does not arrive is said why.
     */
    fun downloadImage(fileName: String, url: String) = pickImages(fileName) { listOfNotNull(linkDownloader.picture(url)) }

    /**
     * Keeps [files] as draft pictures, answering the names they are kept under in their order, a picture brought twice
     * once. One too large or of a format some device cannot show is kept as a copy that fits ([pictureFitter]). One
     * snackbar says what happened: the first refusal, else the first copy made; the rest would only repeat it.
     *
     * @param isWanted Asked under [mutex] before anything is kept: false where the editor the files were for has closed
     *   meanwhile, and nothing is kept.
     */
    suspend fun keep(files: List<ImportedFile>, isWanted: () -> Boolean = { true }): List<String> = mutex.withLock {
        if (!isWanted()) return@withLock emptyList()
        val names = mutableListOf<String>()
        var refusal: Message? = null
        var notice: Message? = null
        files.forEach { file ->
            val outcome = outcomeOf(file) ?: return@withLock emptyList<String>().also { messageSink.sendMessage(Message.OperationFailed) }
            when (val name = outcome.name) {
                null -> if (refusal == null) refusal = outcome.message
                else -> {
                    if (name !in names) names += name
                    if (notice == null) notice = outcome.message
                }
            }
        }
        (refusal ?: notice)?.let { messageSink.sendMessage(it) }
        names
    }

    /** What became of one picture: the [name] it is kept under (null where refused), and the snackbar it asks for. */
    private class Outcome(val name: String?, val message: Message?)

    /** What [file] came to, fitted and then handed to [addDraftImage], or null where it could not be written. */
    private suspend fun outcomeOf(file: ImportedFile): Outcome? = if (file.isTooLarge) {
        Outcome(name = null, message = Message.ImageTooLargeToRead)
    } else {
        try {
            when (val fit = pictureFitter.fit(file.bytes)) {
                is PictureFit.Refused -> Outcome(name = null, message = Message.ImageNotAdded(fit.reason))
                is PictureFit.Usable -> when (val intake = addDraftImage(fit.bytes)) {
                    is ImageIntake.Accepted -> Outcome(name = intake.name, message = fit.notice)
                    is ImageIntake.Refused -> Outcome(name = null, message = Message.ImageNotAdded(intake.reason))
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not keep a picture for the editor: ${exception::class.simpleName}")
            null
        }
    }

    /**
     * Told whether the back stack [hasEditor] whenever it changes, and whether its top before was one, which also covers a
     * stack restored with an editor on it: once the last editor is gone with nothing unsaved ([isUnsaved]), what a saved
     * text named is in the library and what a discarded one named is not wanted, so every draft picture goes. Every way
     * out takes the unsaved text first, so one still there keeps its pictures for the next launch to judge by the stored
     * draft.
     */
    fun onEditorsChanged(hasEditor: Boolean, wasEditorOnTop: Boolean, isUnsaved: () -> Boolean) {
        val hadEditor = this.hasEditor || wasEditorOnTop
        this.hasEditor = hasEditor
        if (hadEditor && !hasEditor && !isUnsaved()) release()
    }

    private var hasEditor = false

    /** Lets go of every draft picture, started at once, so that it takes [mutex] before anything the next editor keeps. */
    private fun release() {
        generation++
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                holdEditorImages(emptySet())
                try {
                    clearDraftImages()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    // Left for the next launch, which deletes every draft picture no stored draft names.
                    println("Could not delete the editor's pictures: ${exception::class.simpleName}")
                }
            }
        }
    }

    /**
     * The text a new song made of [pictures] alone opens with: the directive of each one kept, in order, a line each; null
     * where every one was refused (said in a snackbar), one left unread for its size among them.
     */
    suspend fun pictureSongText(pictures: List<ImportPlan.SongImage>): String? =
        keep(pictures.map { it.bytes?.let { bytes -> ImportedFile(name = it.source, bytes = bytes) } ?: ImportedFile.unread(it.source, isTooLarge = true) })
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n", transform = ChordProImages::directive)

    private companion object {
        const val HOLD_DELAY_MILLIS = 300L
    }
}
