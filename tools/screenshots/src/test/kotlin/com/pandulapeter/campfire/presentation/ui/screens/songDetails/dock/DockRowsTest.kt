/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.ui.test.ExperimentalTestApi
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.opensPlayingSheet
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/** Where an upright phone puts the tempo chip of song details' dock: at the end of the chord diagrams' row, in its bottom end corner. */
@OptIn(ExperimentalTestApi::class)
internal class DockRowsTest {

    @Test
    fun `on an upright phone the tempo chip ends the diagrams' row and the key chip is the app bar's`() = runAppTest {
        openSong()
        waitForIdle()
        assertTempoChipEndsDiagramRow()
    }

    @Test
    fun `without scale suggestions the tempo chip still ends the diagrams' row`() = runAppTest(
        extraPreferences = mapOf("areScaleSuggestionsEnabled" to JsonPrimitive(false)),
    ) {
        openSong()
        waitForIdle()
        assertTempoChipEndsDiagramRow()
    }

    /** The tempo chip stands after the diagram, within its row, under the app bar's key chip. */
    private fun AppTestScope.assertTempoChipEndsDiagramRow() {
        val tempoChip = tempoChip()
        val diagram = diagram()
        val keyChip = onNode(opensPlayingSheet()).fetchSemanticsNode().boundsInRoot
        assertTrue(
            tempoChip.left >= diagram.right && tempoChip.top < diagram.bottom && tempoChip.bottom > diagram.top,
            "The tempo chip at $tempoChip does not end the row of the diagram at $diagram.",
        )
        assertTrue(keyChip.bottom < diagram.top, "The key chip at $keyChip is in the dock, not the app bar.")
    }
}
