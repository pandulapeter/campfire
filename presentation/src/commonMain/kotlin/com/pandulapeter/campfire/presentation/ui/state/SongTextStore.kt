/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentInvalidationsUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSongContentUseCase
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
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
 */
internal class SongTextStore(
    private val scope: CoroutineScope,
    private val backStack: List<CampfireDestination>,
    private val editorDraftFileName: () -> String?,
    private val messageSink: MessageSink,
    private val getSongContent: GetSongContentUseCase,
    private val saveSongContent: SaveSongContentUseCase,
    private val getSongContentInvalidations: GetSongContentInvalidationsUseCase,
) {

    /**
     * The text of the songs the back stack can reach, by file name, read one file at a time as they are opened. Kept in
     * step with the files by [GetSongContentInvalidationsUseCase], see the collector in `init`, and left with only what
     * the screens on the back stack name once a navigation transition has ended, see [pruneSongTexts].
     */
    private val _songTexts = MutableStateFlow(emptyMap<String, String>())
    val songTexts: StateFlow<Map<String, String>> = _songTexts.asStateFlow()

    /**
     * The songs whose files are being renamed right now, under the names they had when it started.
     *
     * A rename moves the file - and with it the song in the library - before the screens that were opened on the old
     * name have been rewritten, since every setlist naming the song and its saved transposition are written in
     * between (see [updateSongFileName]). For those few writes the details screen's destination names a song the
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
            if (isWritten) _songTexts.update { it + (fileName to text) }
        }

    fun loadSongContent(fileName: String) = scope.launch {
        // Cleared first, so that a retry shows the loading state again instead of staying on the error.
        _failedSongFileNames.update { it - fileName }
        val content = getSongContent(fileName)
        if (content == null) {
            _failedSongFileNames.update { it + fileName }
        } else {
            _songTexts.update { it + (content.fileName to content.text) }
        }
    }

    // A sync run, a rescan or a save replaces files underneath the texts held here, and the details header builds
    // its writes on them: without this, toggling a tag would write the text that was open over the version sync
    // had just brought in. The texts are read again rather than only dropped, so a song that is on screen changes
    // in place instead of flashing a loading indicator. An editor with nothing typed in it follows its file (see
    // the editor's FollowFileWhileUntouched); one with text of its own now has unsaved changes, which is what asks
    // the user before their draft replaces the new version — an editor whose file is gone included, where the
    // draft is all there is.
    // Every text held here is read again, not only the one that was named: the invalidations are a state, so that
    // none of them can be lost while this is busy reading, and that state names no file. The texts are few (the
    // screens on the back stack), and those that did not change come out of the repository's cache.
    fun startFollowingFiles() = scope.launch {
        getSongContentInvalidations().drop(1).collect { rereadSongTexts() }
    }

    fun updateSongTexts(update: (Map<String, String>) -> Map<String, String>) = _songTexts.update(update)

    fun updateSongsBeingRenamed(update: (Map<String, Song>) -> Map<String, Song>) = _songsBeingRenamed.update(update)

    private companion object {
        const val SONG_EDIT_ATTEMPTS = 2
    }
}
