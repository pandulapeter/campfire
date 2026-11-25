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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.opensPlayingSheet
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/** Where an upright phone puts the key chip of song details' dock: starting the chord diagrams' row, as tall as it. */
@OptIn(ExperimentalTestApi::class)
internal class DockRowsTest {

    @Test
    fun `on an upright phone the key chip starts the diagrams' row as tall as the diagrams`() = runAppTest {
        openSong()
        waitForIdle()
        assertKeyChipStartsDiagramRow()
    }

    @Test
    fun `without scale suggestions the key chip still starts the diagrams' row`() = runAppTest(
        extraPreferences = mapOf("areScaleSuggestionsEnabled" to JsonPrimitive(false)),
    ) {
        openSong()
        waitForIdle()
        assertKeyChipStartsDiagramRow()
    }

    /** The key chip stands before the diagram in its row, from its top to its bottom, so that a short key is not lost. */
    private fun AppTestScope.assertKeyChipStartsDiagramRow() {
        val keyChip = keyChip()
        val diagram = diagram()
        assertTrue(
            diagram.left >= keyChip.right && keyChip.top <= diagram.top && keyChip.bottom >= diagram.bottom,
            "The diagram at $diagram is not beside the key chip at $keyChip, as tall as it.",
        )
    }

    private fun AppTestScope.keyChip() = onNode(opensPlayingSheet()).fetchSemanticsNode().boundsInRoot

    /**
     * Where the dock draws the diagram of [DOCK_CHORD], the one control said as that chord's shape, its name read without
     * the direction isolates around it.
     */
    private fun AppTestScope.diagram(): Rect = onAllNodes(
        hasClickAction() and SemanticsMatcher("is the diagram of $DOCK_CHORD") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                .any { description -> description.filterNot { it in DIRECTION_ISOLATES }.startsWith("$DOCK_CHORD: ") }
        },
    ).onFirst().fetchSemanticsNode().boundsInRoot
}

/** A chord of House of the Rising Sun that is not its key. */
private const val DOCK_CHORD = "C"

private const val DIRECTION_ISOLATES = "\u2066\u2067\u2068\u2069"
