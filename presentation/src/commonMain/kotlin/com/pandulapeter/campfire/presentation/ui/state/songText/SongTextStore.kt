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

import com.pandulapeter.campfire.presentation.ui.state.songs.SongFilesController
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.domain.api.images.SaveSongAttachmentsResult
import com.pandulapeter.campfire.domain.api.images.SaveSongImageResult
import com.pandulapeter.campfire.domain.api.images.SaveSongImageUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentInvalidationsUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.songs.SaveSongContentUseCase
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.pictures.MissingImagePick
import com.pandulapeter.campfire.presentation.ui.pictures.PictureFit
import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModelCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The text of every song the back stack can reach, read one file at a time as it is opened and kept in step with the
 * files, and the writes to them.
 *
 * @param editorDraftFileName The file the open editor's draft belongs to, whose text is kept for as long as it is open.
 * @param inferredKeys Told every text before [songTexts] has it, so that the key it is in is there in the same frame.
 * @param readImageSizes Handed every text before [songTexts] has it, and returns once the sizes of the pictures they name
 *   are known (`SongImageLoader.loadSizesOf`), so that a song is laid out with its pictures' room from its first frame.
 * @param imageAdding How [addMissingImage] makes a picture fit and stores it with the song naming it.
 * @param addOnBackStackChanged The navigator's, through which a song screen pushed is read ahead, see [readAhead].
 * @param currentSongFileName The page a song details screen is on, see `Navigator.currentSongFileName`.
 */
internal class SongTextStore(
    private val scope: CoroutineScope,
    private val backStack: List<CampfireDestination>,
    private val editorDraftFileName: () -> String?,
    private val messageSink: MessageSink,
    private val getSongContent: GetSongContentUseCase,
    private val saveSongContent: SaveSongContentUseCase,
    private val getSongContentInvalidations: GetSongContentInvalidationsUseCase,
    val inferredKeys: InferredSongKeys,
    private val readImageSizes: suspend (texts: Collection<String>) -> Unit,
    private val imageAdding: SongImageAdding,
    addOnBackStackChanged: (listener: (previousTop: CampfireDestination?, stack: List<CampfireDestination>) -> Unit) -> Unit,
    currentSongFileName: (CampfireDestination.SongDetails) -> String?,
) {

    /**
     * The text of the songs the back stack can reach, by file name, read one file at a time as they are opened. Kept in
     * step with the files by [GetSongContentInvalidationsUseCase], see the collector in `init`, and left with only what
     * the screens on the back stack name once a navigation transition has ended, see [pruneSongTexts].
     */
    private val _songTexts = MutableStateFlow(emptyMap<String, String>())
    val songTexts: StateFlow<Map<String, String>> = _songTexts.asStateFlow()

    /**
     * The models the song pages built of these texts lately, so a song opened again takes its model from memory. Never
     * pruned with the texts ([pruneSongTexts] is about not reading files again): a kept model can only be served for the
     * very text and settings it was built of, so one whose text changed or left simply ages out.
     */
    val lyricsModels = SongLyricsModelCache()

    /**
     * What each song read gave song details' dock, by file name (or an import preview's page name): whether it has a
     * chord diagram to draw and a scale to suggest, each null until a page drawing it with that switch on has said, so a
     * song opened again lays its dock out on its first frame rather than a frame after its page. Main thread only.
     */
    val dockFacts = mutableMapOf<String, Pair<Boolean?, Boolean?>>()

    /**
     * The songs whose files are being renamed right now, under the names they had when it started.
     *
     * A rename moves the file - and with it the song in the library - before the screens that were opened on the old
     * name have been rewritten, since every setlist naming the song and its saved transposition are written in
     * between (see `SongFilesController.updateSongFileName`). For those few writes the details screen's destination names a song the
     * library no longer holds, and a screen that resolved that as "gone" closed itself, or dropped a page and left a
     * setlist on the next song. Resolved through rather than waited out: the song is here from before the file moves
     * until after the back stack names it by its new name, so there is no window to miss and no delay to tune.
     */
    private val _songsBeingRenamed = MutableStateFlow(emptyMap<String, Song>())
    val songsBeingRenamed: StateFlow<Map<String, Song>> = _songsBeingRenamed.asStateFlow()

    /**
     * Held by every write to a song file, from the read the write is built on until [songTexts] has the result. The
     * header's edits are tapped in quick succession on the same file, and two of them working from the same text would
     * each write back the tag the other took off; two saves from the editor would land in whatever order they finished.
     * One lock for every file rather than one per file: the writes are rare and short.
     */
    val songWriteMutex = Mutex()

    /** The file names of the songs whose text could not be read. */
    private val _failedSongFileNames = MutableStateFlow(emptySet<String>())
    val failedSongFileNames: StateFlow<Set<String>> = _failedSongFileNames.asStateFlow()

    /**
     * Lets go of the texts no screen on the back stack names, which would otherwise pile up for as long as the process
     * lives - one per page of every setlist paged through - and be read again by every rescan and sync run. Kept: every
     * file a song details screen names (the pages of a setlist next to the current one are read ahead), the editor's
     * file and the draft's, since [hasUnsavedEditorChanges] compares against them and a missing one reads as unsaved.
     *
     * Once the transition has ended rather than as the back stack changes, because a screen that has been popped is
     * still composed while it slides away, and its page losing its text would put a loading indicator in its place
     * halfway out.
     */
    fun pruneSongTexts() {
        val reachable = buildSet {
            backStack.forEach { destination ->
                when (destination) {
                    is CampfireDestination.SongDetails -> addAll(destination.songFileNames)
                    is CampfireDestination.SongEditor -> add(destination.fileName)
                    else -> Unit
                }
            }
            editorDraftFileName()?.let(::add)
        }
        _songTexts.update { texts -> if (texts.keys.all { it in reachable }) texts else texts.filterKeys { it in reachable } }
    }

    /**
     * Reads the held texts of [fileNames] - every held one for null - back from the files, and applies them in one
     * update, so that the screens reading [songTexts] recompose once rather than once per file. Only to texts that are
     * still held when the reads are done, so that one pruned meanwhile is not brought back.
     */
    private suspend fun rereadSongTexts() {
        val affected = _songTexts.value.keys
        if (affected.isEmpty()) return
        val results = affected.map { name -> name to getSongContent(name)?.text }
        inferredKeys.inferFor(results.mapNotNull { (name, text) -> text?.let { name to it } })
        readImageSizes(results.mapNotNull { it.second })
        _songTexts.update { texts ->
            results.fold(texts) { updated, (name, text) ->
                when {
                    name !in updated -> updated
                    text == null -> updated - name
                    else -> updated + (name to text)
                }
            }
        }
        // The editor keeps what it has, and with no text to compare it to that now counts as unsaved. It is said out
        // loud because saving is what puts the file back, which the user would otherwise have no reason to do.
        results.forEach { (name, text) ->
            if (text == null && editorDraftFileName() == name) {
                messageSink.sendMessage(Message.EditedSongFileGone)
            }
        }
    }

    /**
     * Applies [edit] to the text of a song and writes the result, but only over the text the edit was built on: a
     * file that changed underneath it (a sync run that has not reached [songTexts] yet) is read again and the edit
     * applied to that instead, so the other change is kept rather than written over. Once is enough; a file that
     * keeps changing between the read and the write is reported instead of being chased.
     */
    suspend fun editSongText(fileName: String, edit: (String) -> String) {
        songWriteMutex.withLock {
            repeat(SONG_EDIT_ATTEMPTS) { attempt ->
                // Read inside the lock, so that an edit tapped right after another one starts from what that one wrote.
                // The first attempt may use the text on screen; a retry has to go back to the file.
                val text = songTexts.value[fileName]?.takeIf { attempt == 0 } ?: getSongContent(fileName)?.text ?: return
                val edited = edit(text)
                if (edited == text || withContext(NonCancellable) { writeSongContent(fileName = fileName, text = edited, expectedText = text) }) return
            }
        }
        messageSink.sendMessage(Message.OperationFailed)
    }

    /**
     * The write itself, and [songTexts] brought up to date with it before anything else can read them. The caller holds
     * [songWriteMutex] and runs this on [NonCancellable]: a write that stopped halfway through would leave the file
     * written and the text on screen not.
     *
     * @param expectedText See [SaveSongContentUseCase]: false, and nothing written, when the file no longer holds it.
     */
    suspend fun writeSongContent(fileName: String, text: String, expectedText: String? = null) =
        saveSongContent.invoke(content = SongContent(fileName = fileName, text = text), expectedText = expectedText).also { isWritten ->
            if (isWritten) {
                inferredKeys.inferFor(listOf(fileName to text))
                readImageSizes(listOf(text))
                _songTexts.update { it + (fileName to text) }
            }
        }

    fun loadSongContent(fileName: String) = scope.launch {
        // Cleared first, so that a retry shows the loading state again instead of staying on the error.
        _failedSongFileNames.update { it - fileName }
        val content = getSongContent(fileName)
        if (content == null) {
            _failedSongFileNames.update { it + fileName }
        } else {
            inferredKeys.inferFor(listOf(content.fileName to content.text))
            readImageSizes(listOf(content.text))
            _songTexts.update { it + (content.fileName to content.text) }
        }
    }

    /**
     * Starts reading the texts a song details screen just pushed will show first - its current page and the pages on
     * either side, which the pager composes too - so that they are usually here for its first frame and the first
     * page's in-place model is built before the navigation transition starts moving, not in the middle of it. The
     * current page is [currentFileName], since a pager that comes back on top may have been paged far from where it was
     * opened, or [CampfireDestination.SongDetails.initialIndex] when that names none of its songs. An import preview's
     * pages name no file, so it reads nothing.
     */
    fun readAhead(destination: CampfireDestination.SongDetails, currentFileName: String?) {
        if (destination.importPreview != null) return
        val index = destination.songFileNames.indexOf(currentFileName).takeIf { it >= 0 } ?: destination.initialIndex
        listOfNotNull(
            destination.songFileNames.getOrNull(index),
            destination.songFileNames.getOrNull(index - 1),
            destination.songFileNames.getOrNull(index + 1),
        ).filter { it !in _songTexts.value }.forEach(::loadSongContent)
    }

    /**
     * A sync run, a rescan or a save replaces files underneath the texts held here, and the details header builds
     * its writes on them: without this, toggling a tag would write the text that was open over the version sync
     * had just brought in. The texts are read again rather than only dropped, so a song that is on screen changes
     * in place instead of flashing a loading indicator. An editor with nothing typed in it follows its file (see
     * the editor's FollowFileWhileUntouched); one with text of its own now has unsaved changes, which is what asks
     * the user before their draft replaces the new version — an editor whose file is gone included, where the
     * draft is all there is.
     * Every text held here is read again, not only the one that was named: the invalidations are a state, so that
     * none of them can be lost while this is busy reading, and that state names no file. The texts are few (the
     * screens on the back stack), and those that did not change come out of the repository's cache.
     */
    private fun startFollowingFiles() = scope.launch {
        getSongContentInvalidations().drop(1).collect { rereadSongTexts() }
    }

    /**
     * Picks a file with [pickFiles] for the picture the song [fileName] names [missingName] but the library lacks, makes it
     * fit (a smaller or converted copy, said in a snackbar), and stores it under that song (see [SaveSongImageUseCase]),
     * holding [songWriteMutex] from the read the write is built on, as [editSongText] does: a file changed meanwhile is
     * read again and tried once more. A refused file is said why in a snackbar; picking nothing does nothing, and a song
     * that no longer names [missingName] by then is left alone. Runs in [scope], so the screen may go meanwhile; which
     * missing picture it picks for is kept ([SongImageAdding.pendingPick]) until [pickFiles] returns, for a view model that
     * does not live to see it to be told ([addOrphanedMissingImage]).
     */
    fun addMissingImage(fileName: String, missingName: String, pickFiles: suspend () -> List<ImportedFile>) {
        imageAdding.pendingPick.keep(MissingImagePick(fileName = fileName, missingName = missingName))
        scope.launch {
            val files = try {
                pickFiles()
            } finally {
                imageAdding.pendingPick.keep(null)
            }
            storeMissingImage(fileName = fileName, missingName = missingName, file = files.firstOrNull() ?: return@launch)
        }
    }

    /**
     * Stores the first of [files], picked for [pick] by a view model that is gone, as [addMissingImage] would have: with the
     * same fitting and snackbars, and only while the song still names that missing picture.
     */
    fun addOrphanedMissingImage(pick: MissingImagePick, files: List<ImportedFile>) {
        val file = files.firstOrNull() ?: return
        scope.launch { storeMissingImage(fileName = pick.fileName, missingName = pick.missingName, file = file) }
    }

    /**
     * A missing picture's Download: the picture its address ([missingName]) names, downloaded and stored as
     * [addMissingImage] stores a picked one, the song's directives then naming the library's copy.
     */
    fun downloadMissingImage(fileName: String, missingName: String) {
        scope.launch {
            val file = imageAdding.linkDownloader.picture(missingName) ?: return@launch
            storeMissingImage(fileName = fileName, missingName = missingName, file = file)
        }
    }

    /** The missing picture a pick was under way for when the last view model went, once (`PendingMissingImagePick.take`). */
    fun takePendingMissingImagePick() = imageAdding.pendingPick.take()

    /**
     * Runs under a bare [scope] launch, so a failure to fit or write the picture is a snackbar and a log line here rather
     * than an exception thrown into the view model's scope.
     */
    private suspend fun storeMissingImage(fileName: String, missingName: String, file: ImportedFile) = try {
        writeMissingImage(fileName = fileName, missingName = missingName, file = file)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not store a picture for a missing one: ${exception::class.simpleName}")
        messageSink.sendMessage(Message.OperationFailed)
    }

    private suspend fun writeMissingImage(fileName: String, missingName: String, file: ImportedFile) {
        val picture = when (val fit = imageAdding.fitter.fit(file)) {
            is PictureFit.NotUsable -> return messageSink.sendMessage(fit.message)
            is PictureFit.Usable -> fit
        }
        songWriteMutex.withLock {
            repeat(SONG_EDIT_ATTEMPTS) { attempt ->
                val text = songTexts.value[fileName]?.takeIf { attempt == 0 } ?: getSongContent(fileName)?.text ?: return@withLock
                if (missingName !in ChordProImages.referencedSources(text)) {
                    println("Let go of a picture picked for \"$missingName\", which \"$fileName\" no longer names.")
                    return
                }
                when (val result = imageAdding.saveSongImage(fileName = fileName, expectedText = text, missingName = missingName, bytes = picture.bytes)) {
                    is SaveSongImageResult.Added -> {
                        inferredKeys.inferFor(listOf(fileName to result.text))
                        readImageSizes(listOf(result.text))
                        _songTexts.update { it + (fileName to result.text) }
                        picture.notice?.let { messageSink.sendMessage(it) }
                        return
                    }
                    is SaveSongImageResult.Refused -> return messageSink.sendMessage(Message.ImageNotAdded(result.reason))
                    SaveSongImageResult.SongChanged -> Unit
                }
            }
        }
        messageSink.sendMessage(Message.OperationFailed)
    }

    /**
     * Writes the song [fileName] with [save] (`SaveSongAttachmentsUseCase`, which moves the files it names into the library
     * first), given the text the write is built on, holding [songWriteMutex] as [editSongText] does: a file changed
     * meanwhile is read again and tried once more. Answers whether the file now holds them.
     */
    suspend fun writeAttachments(fileName: String, save: suspend (expectedText: String) -> SaveSongAttachmentsResult): Boolean {
        songWriteMutex.withLock {
            repeat(SONG_EDIT_ATTEMPTS) { attempt ->
                val text = songTexts.value[fileName]?.takeIf { attempt == 0 } ?: getSongContent(fileName)?.text ?: return false
                val result = save(text)
                if (result is SaveSongAttachmentsResult.Saved) {
                    _songTexts.update { it + (fileName to result.text) }
                    return true
                }
            }
        }
        messageSink.sendMessage(Message.OperationFailed)
        return false
    }

    fun updateSongTexts(update: (Map<String, String>) -> Map<String, String>) = _songTexts.update(update)

    fun updateSongsBeingRenamed(update: (Map<String, Song>) -> Map<String, Song>) = _songsBeingRenamed.update(update)

    private companion object {
        const val SONG_EDIT_ATTEMPTS = 2
    }

    init {
        startFollowingFiles()
        // Every way a song screen is pushed passes here, inside the click and before the screen's first frame, and the
        // pushed destination is on the back stack before pruneSongTexts can run, so a text read ahead is never pruned.
        addOnBackStackChanged { previousTop, stack ->
            val top = stack.lastOrNull()
            if (top is CampfireDestination.SongDetails && top != previousTop) {
                readAhead(top, currentFileName = currentSongFileName(top))
            }
        }
    }
}
