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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onFirst
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.opensPlayingSheet
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/** Where an upright phone puts the key chip of song details' dock: over the tuner's pill, starting the chord diagrams' row. */
@OptIn(ExperimentalTestApi::class)
internal class DockRowsTest {

    @Test
    fun `on an upright phone the key chip stands over the tuner at the start of the diagrams' row`() = runAppTest {
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

    @Test
    fun `with the dock's tuner switched off the key chip stands alone and the menu holds the tuner`() = runAppTest(
        extraPreferences = mapOf("isDockTunerEnabled" to JsonPrimitive(false)),
    ) {
        openSong()
        waitForIdle()
        onNode(hasContentDescription(string(Res.string.tuner), substring = true) and hasClickAction()).assertDoesNotExist()
        tap(hasContentDescription(string(Res.string.songs_actions)))
        onNode(hasText(string(Res.string.tuner)) and hasAnyAncestor(isPopup())).assertExists()
    }

    @Test
    fun `with an instrument without shapes the tuner stands beside the key chip in the one row`() = runAppTest(
        extraPreferences = mapOf("instrument" to JsonPrimitive("violin")),
    ) {
        openSong()
        waitForIdle()
        val keyChip = keyChip()
        val tuner = onNode(hasContentDescription(string(Res.string.tuner), substring = true) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(
            tuner.right <= keyChip.left && tuner.bottom > keyChip.top && tuner.top < keyChip.bottom,
            "The tuner at $tuner does not stand before the key chip at $keyChip.",
        )
    }

    /** The key chip and the tuner's pill under it stand before the diagram, within its row, so neither is lost. */
    private fun AppTestScope.assertKeyChipStartsDiagramRow() {
        val keyChip = keyChip()
        val tuner = onNode(hasContentDescription(string(Res.string.tuner), substring = true) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val diagram = diagram()
        assertTrue(
            diagram.left >= keyChip.right && keyChip.top >= diagram.top && tuner.top >= keyChip.bottom && tuner.bottom <= diagram.bottom,
            "The key chip at $keyChip and the tuner at $tuner do not stand over each other before the diagram at $diagram.",
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
