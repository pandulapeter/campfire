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
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.useCases.GetEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentInvalidationsUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSongContentUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import com.pandulapeter.campfire.presentation.ui.state.SongTextStore
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
        lateinit var session: EditorSession
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
                updateBackStack = { update -> backStack.update() },
                popBackStack = { backStack.removeAt(backStack.lastIndex) },
                takePendingExit = { null },
                requestExit = { _, _ -> },
            )
        }

        val messages get() = messageSink.messageQueue.value.map { it.value }
    }

    private fun TestScope.fixture(notation: UserPreferences.Notation = UserPreferences.Notation.STANDARD) = Fixture(backgroundScope, notation)

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
