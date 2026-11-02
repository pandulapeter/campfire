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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_editor_discard
import com.pandulapeter.campfire.presentation.resources.song_editor_unsaved_changes
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.songs_edit_song
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOME_ON_THE_RANGE
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class AppExitTest {

    @Test
    fun `the exit writes the text size, metronome, tuner, and export settings that were still waiting`() = runAppTest {
        setWaitingPreferences()
        runBlocking { viewModel.settleSynchronizationBeforeExit() }
        assertWaitingPreferencesWritten(storedPreferences())
    }

    @Test
    fun `clearing the view model writes the values that were still waiting`() = runAppTest {
        setWaitingPreferences()
        runOnIdle { viewModel.appExitController.onCleared() }
        waitForLibrary { storedPreferences()["fontScale"]?.jsonPrimitive?.float == FONT_SCALE }
        assertWaitingPreferencesWritten(storedPreferences())
    }

    @Test
    fun `the exit writes a tempo and a capo still waiting, a library song's to the preferences and a setlist song's to the setlist`() =
        runAppTest(setlists = listOf(SETLIST)) {
            // The house song names no capo, and the other one the second fret.
            runOnIdle {
                viewModel.overrides.setTempo(songFileName = HOUSE_OF_THE_RISING_SUN, setlistFileName = null, bpm = LIBRARY_TEMPO)
                viewModel.overrides.stepCapo(songFileName = HOUSE_OF_THE_RISING_SUN, setlistFileName = null, delta = 2)
                viewModel.overrides.setTempo(songFileName = HOME_ON_THE_RANGE, setlistFileName = SETLIST, bpm = SETLIST_TEMPO)
                viewModel.overrides.stepCapo(songFileName = HOME_ON_THE_RANGE, setlistFileName = SETLIST, delta = 1)
            }
            runBlocking { viewModel.settleSynchronizationBeforeExit() }
            val preferences = storedPreferences()
            assertEquals(LIBRARY_TEMPO, preferences.overrideOf("tempos", HOUSE_OF_THE_RISING_SUN))
            assertEquals(2, preferences.overrideOf("capos", HOUSE_OF_THE_RISING_SUN))
            assertEquals(null, preferences.overrideOf("tempos", HOME_ON_THE_RANGE))
            val entry = storedSetlist(SETLIST).getValue("songs").jsonArray.map { it.jsonObject }
                .single { it.getValue("file").jsonPrimitive.content == HOME_ON_THE_RANGE }
            assertEquals(SETLIST_TEMPO, entry["tempo"]?.jsonPrimitive?.intOrNull)
            assertEquals(3, entry["capo"]?.jsonPrimitive?.intOrNull)
        }

    @Test
    fun `going to the background stores the editor's unsaved text and removes it once there is none`() = runAppTest {
        openEditor()
        typeLine()
        runOnIdle { viewModel.appExitController.onAppPaused() }
        waitForLibrary { editorDraft().let { it.isFile && TYPED_TEXT in it.readText() } }
        onNodeWithContentDescription(string(Res.string.save)).performClick()
        waitForLibrary { TYPED_TEXT in File(songsFolder, HOUSE_OF_THE_RISING_SUN).readText() }
        waitForIdle()
        runOnIdle { viewModel.appExitController.onAppPaused() }
        waitForLibrary { !editorDraft().exists() }
    }

    @Test
    fun `an exit with unsaved editor text asks first and runs only after Discard`() = runAppTest {
        openEditor()
        typeLine()
        var exits = 0
        var cancellations = 0
        runOnIdle { viewModel.appExitController.requestExit(onExit = { exits++ }, onCancelled = { cancellations++ }) }
        waitForIdle()
        onNodeWithText(string(Res.string.song_editor_unsaved_changes)).assertExists()
        assertEquals(0, exits)
        onNode(hasText(string(Res.string.song_editor_discard)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitForIdle()
        assertEquals(1, exits)
        assertEquals(0, cancellations)
    }

    @Test
    fun `staying, or a second exit request, cancels the exit that asked`() = runAppTest {
        openEditor()
        typeLine()
        var exits = 0
        var cancellations = 0
        runOnIdle { viewModel.appExitController.requestExit(onExit = { exits++ }, onCancelled = { cancellations++ }) }
        waitForIdle()
        onNode(hasText(string(Res.string.cancel)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitForIdle()
        assertEquals(0, exits)
        assertEquals(1, cancellations)
        var secondExits = 0
        runOnIdle { viewModel.appExitController.requestExit(onExit = { exits++ }, onCancelled = { cancellations++ }) }
        waitForIdle()
        runOnIdle { viewModel.appExitController.requestExit(onExit = { secondExits++ }, onCancelled = {}) }
        waitForIdle()
        assertEquals(0, exits)
        assertEquals(2, cancellations)
        onNodeWithText(string(Res.string.song_editor_unsaved_changes)).assertExists()
        onNode(hasText(string(Res.string.song_editor_discard)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitForIdle()
        assertEquals(1, secondExits)
        assertEquals(0, exits)
    }

    /** Sets a value of each debounced preference through the setter its screen calls, none of them written yet. */
    private fun AppTestScope.setWaitingPreferences() = runOnIdle {
        viewModel.fontScaleController.setFontScale(FONT_SCALE)
        viewModel.metronomeController.updateMetronomeSettings { copy(bpm = METRONOME_TEMPO) }
        viewModel.tunerController.updateTunerSettings { copy(referencePitch = REFERENCE_PITCH) }
        viewModel.exportController.setPrintSettings(PrintSettings(fontSize = PRINT_FONT_SIZE))
    }

    private fun assertWaitingPreferencesWritten(preferences: JsonObject) {
        assertEquals(FONT_SCALE, preferences.getValue("fontScale").jsonPrimitive.float)
        assertEquals(METRONOME_TEMPO, preferences.getValue("metronomeSettings").jsonObject.getValue("bpm").jsonPrimitive.int)
        assertEquals(REFERENCE_PITCH, preferences.getValue("tunerSettings").jsonObject.getValue("referencePitch").jsonPrimitive.int)
        assertEquals(PRINT_FONT_SIZE, preferences.getValue("printSettings").jsonObject.getValue("fontSize").jsonPrimitive.int)
    }

    private fun JsonObject.overrideOf(key: String, songFileName: String) = get(key)?.jsonObject?.get(songFileName)?.jsonPrimitive?.intOrNull

    /** Opens the house song's editor from Songs, through its details screen's actions. */
    private fun AppTestScope.openEditor() {
        tap(hasText(RISING_SUN_TITLE) and hasClickAction())
        tap(hasContentDescription(string(Res.string.songs_actions)))
        tap(hasText(string(Res.string.songs_edit_song)) and hasClickAction())
    }

    private fun AppTestScope.tap(matcher: SemanticsMatcher) {
        onNode(matcher).performTouchInput { click() }
        waitForIdle()
    }

    private fun AppTestScope.typeLine() {
        onNode(hasSetTextAction()).performTextInput("\n$TYPED_TEXT")
        waitForIdle()
        assertTrue(runOnIdle { viewModel.editorSession.hasUnsavedEditorText() }, "The typed line left nothing unsaved.")
    }

    private fun AppTestScope.editorDraft() = File(dataDirectory, "preferences/editor-draft.json")
}

private const val SETLIST = "campfire_tests-two_songs.setlist.json"
private const val RISING_SUN_TITLE = "House of the Rising Sun"
private const val TYPED_TEXT = "A line typed by a test"
private const val FONT_SCALE = 1.5f
private const val METRONOME_TEMPO = 97
private const val REFERENCE_PITCH = 432
private const val PRINT_FONT_SIZE = 16
private const val LIBRARY_TEMPO = 97
private const val SETLIST_TEMPO = 98
