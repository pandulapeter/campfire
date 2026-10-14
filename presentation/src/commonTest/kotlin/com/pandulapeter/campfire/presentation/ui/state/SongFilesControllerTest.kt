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

import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.models.SongFileRename
import com.pandulapeter.campfire.domain.api.useCases.CreateSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.DuplicateSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.RenameSongFileUseCase
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongFilesControllerTest {

    private val calls = mutableListOf<String>()
    private var songTextKeysWhenScreensFollowed: Set<String>? = null
    private var songsBeingRenamedWhenRenamed: Map<String, Song>? = null
    private var rename: suspend (Song) -> SongFileRename? = { null }
    private var haveReferencesBeenRemoved = true
    private lateinit var songTextStore: SongTextStore
    private lateinit var messageSink: MessageSink

    private fun TestScope.controller(): SongFilesController {
        messageSink = MessageSink(backgroundScope)
        val files = FakeSongFiles()
        songTextStore = files.songTextStore(backgroundScope, messageSink)
        return SongFilesController(
            messageSink = messageSink,
            songTextStore = songTextStore,
            showDialog = {},
            duplicateSongDialog = { _, _ -> error("Not used.") },
            openEditor = { _, _ -> },
            clearEditorDraft = { calls += "draft" },
            followMetronomeRename = { _, _ -> calls += "metronome" },
            followScreensRename = { _, _ ->
                songTextKeysWhenScreensFollowed = songTextStore.songTexts.value.keys
                calls += "screens"
            },
            closeScreensShowing = { calls += "close" },
            followReportedFileNames = { calls += "reported" },
            getSongContent = files,
            createSongFile = object : CreateSongUseCase {
                override suspend fun invoke(title: String, artist: String, metadata: Map<ChordProMetadataFields.Field, String>) = error("Not used.")
            },
            duplicateSongFile = object : DuplicateSongUseCase {
                override suspend fun invoke(fileName: String, changes: Map<ChordProMetadataFields.Field, String>) = error("Not used.")
            },
            renameSongFile = object : RenameSongFileUseCase {
                override suspend fun invoke(song: Song): SongFileRename? {
                    songsBeingRenamedWhenRenamed = songTextStore.songsBeingRenamed.value
                    return rename(song)
                }
            },
            deleteSongFile = object : DeleteSongUseCase {
                override suspend fun invoke(fileName: String) = haveReferencesBeenRemoved
            },
        )
    }

    @Test
    fun `a song is held as being renamed from before the file moves until the screens have followed`() = runTest {
        val controller = controller()
        songTextStore.updateSongTexts { mapOf("old.cho" to "{title: Old}") }
        rename = { SongFileRename(fileName = "new.cho", haveReferencesFollowed = true) }
        controller.updateSongFileName(song("old.cho"))
        runCurrent()
        assertEquals(setOf("old.cho"), songsBeingRenamedWhenRenamed?.keys)
        assertEquals(listOf("metronome", "screens", "reported"), calls)
        assertEquals(setOf("new.cho"), songTextKeysWhenScreensFollowed)
        assertTrue(songTextStore.songsBeingRenamed.value.isEmpty())
    }

    @Test
    fun `a rename that moves nothing or fails lets go of the song and follows nothing`() = runTest {
        val controller = controller()
        controller.updateSongFileName(song("old.cho"))
        runCurrent()
        assertTrue(songTextStore.songsBeingRenamed.value.isEmpty())
        rename = { error("The file could not be moved.") }
        controller.updateSongFileName(song("old.cho"))
        runCurrent()
        assertTrue(songTextStore.songsBeingRenamed.value.isEmpty())
        assertEquals(emptyList(), calls)
        assertEquals(Message.OperationFailed, messageSink.messageQueue.value.last().value)
    }

    @Test
    fun `a rename whose references did not all follow says so after the screens followed`() = runTest {
        val controller = controller()
        rename = { SongFileRename(fileName = "new.cho", haveReferencesFollowed = false) }
        controller.updateSongFileName(song("old.cho"))
        runCurrent()
        assertEquals(Message.SongFileRenamedPartly, messageSink.messageQueue.value.last().value)
    }

    @Test
    fun `a deleted song lets go of the draft before the screens close`() = runTest {
        val controller = controller()
        songTextStore.updateSongTexts { mapOf("gone.cho" to "{title: Gone}") }
        controller.deleteSong("gone.cho")
        runCurrent()
        assertEquals(listOf("draft", "close", "reported"), calls)
        assertTrue("gone.cho" !in songTextStore.songTexts.value)
    }

    private fun song(fileName: String) = Song(
        fileName = fileName, title = fileName, artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = true, lastModified = 0, size = 0,
    )
}
