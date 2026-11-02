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

import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ModeEndingsTest {

    private var reorderingEnds = 0
    private var selectionEnds = 0
    private val modeEndings = ModeEndings(endSetlistReordering = { reorderingEnds++ }, endSongSelection = { selectionEnds++ })

    @Test
    fun `leaving the setlists ends reordering`() {
        modeEndings.onBackStackChanged(listOf(CampfireDestination.Songs, CampfireDestination.Setlists))
        assertEquals(0, reorderingEnds)
        modeEndings.onBackStackChanged(listOf(CampfireDestination.Songs, CampfireDestination.Settings))
        assertEquals(1, reorderingEnds)
    }

    @Test
    fun `editing a setlist ends reordering`() {
        modeEndings.onBeforeDialogChange(DialogType.Instrument)
        assertEquals(0, reorderingEnds)
        modeEndings.onBeforeDialogChange(
            DialogType.EditSetlist(
                Setlist(
                    fileName = "gig.setlist.json", title = "Gig", description = "", date = LocalDate(2026, 1, 1), isArchived = false,
                    entries = emptyList(), size = 0L,
                ),
            ),
        )
        assertEquals(1, reorderingEnds)
    }

    @Test
    fun `leaving the songs ends the selection`() {
        modeEndings.onBackStackChanged(listOf(CampfireDestination.Songs))
        assertEquals(0, selectionEnds)
        modeEndings.onBackStackChanged(listOf(CampfireDestination.Songs, CampfireDestination.Setlists))
        assertEquals(1, selectionEnds)
    }

    @Test
    fun `switching performance mode on ends the selection`() = runTest {
        val isPerformanceModeEnabled = MutableStateFlow(false)
        modeEndings.start(backgroundScope, isPerformanceModeEnabled)
        runCurrent()
        assertEquals(0, selectionEnds)
        isPerformanceModeEnabled.value = true
        runCurrent()
        assertEquals(1, selectionEnds)
    }
}
