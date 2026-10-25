/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.TextRange
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.api.songs.CreateSongFromTextUseCase
import com.pandulapeter.campfire.domain.api.songs.DraftSongFromTextUseCase
import com.pandulapeter.campfire.domain.api.songs.GetEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentInvalidationsUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.songs.SaveEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.songs.SaveSongContentUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.followNewSongCreated
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextTitle
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import com.pandulapeter.campfire.presentation.ui.state.songText.SongTextStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The editor's session over a library of [files] held in memory, read and written through small stand-ins for the use cases. */
class EditorSessionTest {

    private val files = mutableMapOf<String, String>()
    private var storedDraft: SongContent? = null
    private var isWriteFailing = false
    private val backStack = mutableStateListOf<CampfireDestination>(CampfireDestination.Songs)

    private inner class Fixture(scope: CoroutineScope, notation: UserPreferences.Notation) {
        val messageSink = MessageSink(scope)
        val dialogHost = DialogHost(scope)
        private val getSongContent = object : GetSongContentUseCase {
            override suspend fun invoke(fileName: String) = files[fileName]?.let { SongContent(fileName = fileName, text = it) }
        }
        val session: EditorSession
        val songTextStore = SongTextStore(
            scope = scope,
            backStack = backStack,
            editorDraftFileName = { session.editorDraft.value?.fileName },
            messageSink = messageSink,
            getSongContent = getSongContent,
            saveSongContent = object : SaveSongContentUseCase {
                override suspend fun invoke(content: SongContent, expectedText: String?): Boolean {
                    if (isWriteFailing) throw IllegalStateException("The disk is full.")
                    files[content.fileName] = content.text
                    return true
                }
            },
            getSongContentInvalidations = object : GetSongContentInvalidationsUseCase {
                override fun invoke() = emptyFlow<Long>()
            },
        )

        init {
            session = EditorSession(
                scope = scope,
                dialogHost = dialogHost,
                messageSink = messageSink,
                songTextStore = songTextStore,
                songRenderer = testSongRenderer(),
                backStack = backStack,
                userPreferences = MutableStateFlow(preferences(notation)),
                arePreferencesLoaded = MutableStateFlow(true),
                getSongContent = getSongContent,
                getEditorDraft = object : GetEditorDraftUseCase {
                    override suspend fun invoke() = storedDraft
                },
                saveEditorDraft = object : SaveEditorDraftUseCase {
                    override suspend fun invoke(draft: SongContent?) {
                        storedDraft = draft
                    }
                },
                draftSongFromText = object : DraftSongFromTextUseCase {
                    override fun invoke(
                        text: String,
                        placeholderTitle: String,
                        placeholderArtist: String,
                        suggestedTitle: String?,
                        suggestedArtist: String?,
                    ) = if ("{title" in text) text else "{title: ${suggestedTitle ?: placeholderTitle}}\n{artist: ${suggestedArtist ?: placeholderArtist}}\n$text"
                },
                createSongFromText = object : CreateSongFromTextUseCase {
                    override suspend fun invoke(text: String): Song {
                        val title = Regex("""\{title: (.*)}""").find(text)?.groupValues?.get(1).orEmpty()
                        val fileName = title.lowercase().replace(' ', '_') + ".cho"
                        files[fileName] = text
                        return Song(
                            fileName = fileName, title = title, artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
                            coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
                        )
                    }
                },
                updateBackStack = { update -> backStack.update() },
                isSongShownOnTop = { fileName -> (backStack.lastOrNull() as? CampfireDestination.SongDetails)?.songFileNames?.singleOrNull() == fileName },
                popBackStack = { backStack.removeAt(backStack.lastIndex) },
                takePendingExit = { null },
                requestExit = { _, _ -> },
                followNewSongCreated = { fileName -> backStack.followNewSongCreated(fileName) },
            )
        }

        val messages get() = messageSink.messageQueue.value.map { it.value }
    }

    private fun TestScope.fixture(notation: UserPreferences.Notation = UserPreferences.Notation.STANDARD) = Fixture(backgroundScope, notation)

    @Test
    fun `the editor opened from a list has the song's details screen under it`() = runTest {
        fixture().session.openEditor("a.cho")
        val details = backStack[1] as CampfireDestination.SongDetails
        assertEquals(listOf("a.cho"), details.songFileNames)
        assertEquals(listOf(CampfireDestination.Songs, details, CampfireDestination.SongEditor(fileName = "a.cho")), backStack.toList())
    }

    @Test
    fun `the editor opened from the song's details screen goes straight on top of it`() = runTest {
        val details = CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0)
        backStack += details
        fixture().session.openEditor("a.cho")
        assertEquals(listOf(CampfireDestination.Songs, details, CampfireDestination.SongEditor(fileName = "a.cho")), backStack.toList())
    }

    @Test
    fun `a stored draft the file already holds is thrown away rather than reopened`() = runTest {
        files["a.cho"] = "{key: Bb}\n[Bb]One"
        storedDraft = SongContent(fileName = "a.cho", text = "{key: Bb}\n[Bb]One")
        val fixture = fixture(UserPreferences.Notation.GERMAN)
        fixture.session.startRecovery()
        assertFalse(fixture.session.editorDraftRecovery.await())
        runCurrent()
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Songs), backStack.toList())
        assertNull(storedDraft)
    }

    @Test
    fun `a stored draft of a file that is gone reopens the editor and says both`() = runTest {
        storedDraft = SongContent(fileName = "a.cho", text = "[C]One")
        val fixture = fixture()
        fixture.session.startRecovery()
        assertTrue(fixture.session.editorDraftRecovery.await())
        assertEquals(CampfireDestination.SongEditor(fileName = "a.cho"), backStack.last())
        assertEquals("[C]One", fixture.session.editorDraft.value?.text)
        assertTrue(fixture.session.hasUnsavedEditorText())
        assertEquals(listOf(Message.EditorDraftRestored, Message.EditedSongFileGone), fixture.messages)
    }

    @Test
    fun `a stored draft is not recovered over an editor the stack already has`() = runTest {
        files["a.cho"] = "[C]One"
        storedDraft = SongContent(fileName = "a.cho", text = "[C]Two")
        backStack += CampfireDestination.SongEditor(fileName = "b.cho")
        val fixture = fixture()
        fixture.session.startRecovery()
        assertFalse(fixture.session.editorDraftRecovery.await())
        assertEquals(CampfireDestination.SongEditor(fileName = "b.cho"), backStack.last())
        assertNull(fixture.session.recoveredEditorField("a.cho"))
        assertNull(fixture.session.recoveredEditorField("b.cho"))
        assertEquals(emptyList(), fixture.messages)
    }

    @Test
    fun `a stored draft is handed to an editor the restored stack has for its file, and kept`() = runTest {
        files["a.cho"] = "[C]One"
        storedDraft = SongContent(fileName = "a.cho", text = "[C]Two")
        backStack += CampfireDestination.SongEditor(fileName = "a.cho")
        val fixture = fixture()
        fixture.session.startRecovery()
        assertFalse(fixture.session.editorDraftRecovery.await())
        runCurrent()
        assertEquals("[C]Two", fixture.session.recoveredEditorField("a.cho")?.text?.toString())
        assertEquals("[C]Two", fixture.session.editorDraft.value?.text)
        assertEquals(SongContent(fileName = "a.cho", text = "[C]Two"), storedDraft)
        assertEquals(emptyList(), fixture.messages)
    }

    @Test
    fun `a chord typed in the reader's notation is not unsaved against the file's spelling of it`() = runTest {
        files["a.cho"] = "[Bb]One"
        backStack += CampfireDestination.SongEditor(fileName = "a.cho")
        val fixture = fixture(UserPreferences.Notation.GERMAN)
        fixture.songTextStore.loadSongContent("a.cho")
        runCurrent()
        fixture.session.onEditorTextChanged(fileName = "a.cho", text = "[B]One")
        assertFalse(fixture.session.hasUnsavedEditorText())
        fixture.session.onEditorTextChanged(fileName = "a.cho", text = "[H]One")
        assertTrue(fixture.session.hasUnsavedEditorText())
    }

    @Test
    fun `a save from the unsaved changes question that fails keeps the editor open with its text`() = runTest {
        files["a.cho"] = "[C]One"
        backStack += CampfireDestination.SongEditor(fileName = "a.cho")
        isWriteFailing = true
        val fixture = fixture()
        fixture.session.onEditorTextChanged(fileName = "a.cho", text = "[C]Two")
        fixture.dialogHost.showDialog(DialogType.UnsavedChanges)
        fixture.session.saveEditorChangesAndLeave()
        runCurrent()
        assertEquals(CampfireDestination.SongEditor(fileName = "a.cho"), backStack.last())
        assertEquals("[C]Two", fixture.session.editorDraft.value?.text)
        assertNull(fixture.dialogHost.visibleDialog.value)
        assertEquals(listOf<Message>(Message.SaveFailed), fixture.messages)
        assertEquals("[C]One", files["a.cho"])
    }

    @Test
    fun `a save from the unsaved changes question that succeeds leaves the editor`() = runTest {
        files["a.cho"] = "[C]One"
        backStack += CampfireDestination.SongEditor(fileName = "a.cho")
        val fixture = fixture()
        fixture.session.onEditorTextChanged(fileName = "a.cho", text = "[C]Two")
        fixture.dialogHost.showDialog(DialogType.UnsavedChanges)
        fixture.session.saveEditorChangesAndLeave()
        runCurrent()
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Songs), backStack.toList())
        assertEquals("[C]Two", files["a.cho"])
    }

    @Test
    fun `a new song opens its editor alone, with a placeholder title, unsaved, and its draft on disk at once`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor("[G]Hello", null)
        val editor = backStack.last() as CampfireDestination.SongEditor
        assertTrue(editor.isNewSong)
        assertEquals(listOf(CampfireDestination.Songs, editor), backStack.toList())
        assertEquals("{title: New song}\n{artist: Unknown artist}\n[G]Hello", fixture.session.editorDraft.value?.text)
        assertTrue(fixture.session.hasUnsavedEditorText())
        assertEquals(fixture.session.editorDraft.value, storedDraft)
        assertTrue(files.isEmpty())
    }

    @Test
    fun `a suggested title is selected in a new song's editor`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor("[G]Hello", SharedTextTitle(title = "Riverlight", artist = "The Invented Band"))
        assertEquals("{title: Riverlight}\n{artist: The Invented Band}\n[G]Hello", fixture.session.editorDraft.value?.text)
        val field = fixture.session.retainedEditorField(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME)
        assertEquals(TextRange(8, 18), field?.selection)
    }

    @Test
    fun `a new song replaces an editor on top that has nothing unsaved`() = runTest {
        files["a.cho"] = "[C]One"
        backStack += CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0)
        backStack += CampfireDestination.SongEditor(fileName = "a.cho")
        fixture().session.openNewSongEditor("[G]Hello", null)
        assertEquals(3, backStack.size)
        assertTrue((backStack.last() as CampfireDestination.SongEditor).isNewSong)
    }

    @Test
    fun `a new song with no title is not saved`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor("[G]Hello", null)
        fixture.session.saveSongContent(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME, "{title: }\n[G]Hello")
        runCurrent()
        assertTrue(files.isEmpty())
        assertEquals(listOf<Message>(Message.NewSongUntitled), fixture.messages)
        assertTrue((backStack.last() as CampfireDestination.SongEditor).isNewSong)
    }

    @Test
    fun `a new song's first save writes it, and its editor goes on over the song's details screen`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor("[G]Hello", null)
        val entryKey = (backStack.last() as CampfireDestination.SongEditor).entryKey
        val text = "{title: Song}\n[G]Hello"
        fixture.session.onEditorTextChanged(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME, text)
        fixture.session.saveSongContent(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME, text)
        runCurrent()
        assertEquals(text, files["song.cho"])
        val details = backStack[1] as CampfireDestination.SongDetails
        assertEquals(listOf("song.cho"), details.songFileNames)
        assertEquals(CampfireDestination.SongEditor(fileName = "song.cho", entryKey = entryKey), backStack.last())
        assertEquals("song.cho", fixture.session.editorDraft.value?.fileName)
        assertFalse(fixture.session.hasUnsavedEditorText())
    }

    @Test
    fun `a new song saved from the unsaved changes question leaves for its details screen`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor("{title: Song}\n[G]Hello", null)
        fixture.dialogHost.showDialog(DialogType.UnsavedChanges)
        fixture.session.saveEditorChangesAndLeave()
        runCurrent()
        assertEquals(CampfireDestination.Songs, backStack.first())
        assertEquals(listOf("song.cho"), (backStack.last() as CampfireDestination.SongDetails).songFileNames)
        assertEquals(2, backStack.size)
    }

    @Test
    fun `a new song discarded leaves nothing behind`() = runTest {
        val fixture = fixture()
        // What lets go of the stored draft once nothing is unsaved, as every start does.
        fixture.session.startRecovery()
        fixture.session.editorDraftRecovery.await()
        fixture.session.openNewSongEditor("[G]Hello", null)
        runCurrent()
        assertTrue(storedDraft != null)
        fixture.session.leaveEditorWithoutSaving()
        runCurrent()
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Songs), backStack.toList())
        assertTrue(files.isEmpty())
        assertNull(storedDraft)
    }

    @Test
    fun `a stored draft of a new song reopens its editor alone, with no word of a missing file`() = runTest {
        storedDraft = SongContent(fileName = CampfireDestination.SongEditor.NEW_SONG_FILE_NAME, text = "{title: Song}\n[G]Hello")
        val fixture = fixture()
        fixture.session.startRecovery()
        assertTrue(fixture.session.editorDraftRecovery.await())
        assertEquals(2, backStack.size)
        assertTrue((backStack.last() as CampfireDestination.SongEditor).isNewSong)
        assertEquals(listOf<Message>(Message.EditorDraftRestored), fixture.messages)
    }

    private fun preferences(notation: UserPreferences.Notation) = UserPreferences(
        isPerformanceModeEnabled = false,
        shouldShowArchivedSetlists = false,
        areChordsEnabled = true,
        areSetlistsEnabled = true,
        isMetronomeEnabled = true,
        fontScale = UserPreferences.DEFAULT_FONT_SCALE,
        sortingMode = UserPreferences.SortingMode.BY_TITLE,
        setlistSortingMode = UserPreferences.SetlistSortingMode.BY_DATE,
        uiMode = UserPreferences.UiMode.SYSTEM_DEFAULT,
        themeColor = UserPreferences.ThemeColor.CAMPFIRE,
        isAppIconThemed = true,
        isCoverArtEnabled = true,
        shouldNumberSections = true,
        language = UserPreferences.Language.SYSTEM_DEFAULT,
        chordSpelling = UserPreferences.ChordSpelling(accidentals = UserPreferences.Accidentals.ORIGINAL, notation = notation),
        transpositions = emptyMap(),
        foldedSections = emptyMap(),
        tagMatchMode = UserPreferences.MatchMode.ANY,
        languageMatchMode = UserPreferences.MatchMode.ANY,
        tagSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
        languageSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
    )
}
