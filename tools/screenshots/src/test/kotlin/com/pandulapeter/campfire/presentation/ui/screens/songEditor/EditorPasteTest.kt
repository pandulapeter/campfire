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

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.openEditor
import com.pandulapeter.campfire.screenshots.runAppTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class EditorPasteTest {

    @Test
    fun `a chord sheet pasted into the editor is saved as ChordPro set apart from the song`() = runAppTest {
        openEditor()
        val field = onNode(hasSetTextAction())
        field.performTextInputSelection(TextRange(field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text.length))
        field.performTextInput(PASTED)
        waitForIdle()
        onNodeWithContentDescription(string(Res.string.save)).performClick()
        val song = File(songsFolder, HOUSE_OF_THE_RISING_SUN)
        waitForLibrary { "Sing it" in song.readText() }
        val text = song.readText()
        assertTrue("\n\n{start_of_chorus: Chorus}\n[G]Sing it [D]loud\n{end_of_chorus}\n" in text, text)
        assertTrue(text.endsWith("{end_of_chorus}\n"), text)
    }
}

/** Chords written over the lyrics, as a page in a browser copies them, run straight on from the song's last line. */
private const val PASTED = "Chorus:\nG       D\nSing it loud\n"
