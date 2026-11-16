/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.spreadTheWord

import com.pandulapeter.campfire.data.model.preferences.SongOpens
import com.pandulapeter.campfire.presentation.ui.firstRun.DemoLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpreadTheWordRulesTest {

    @Test
    fun `the first open counts one open on one day`() {
        assertEquals(SongOpens(count = 1, dayCount = 1, lastEpochDay = 100), SongOpens().counted(today = 100))
    }

    @Test
    fun `another open on the same day counts the open but not the day`() {
        assertEquals(SongOpens(count = 4, dayCount = 2, lastEpochDay = 100), SongOpens(count = 3, dayCount = 2, lastEpochDay = 100).counted(today = 100))
    }

    @Test
    fun `an open on a new day counts both`() {
        assertEquals(SongOpens(count = 4, dayCount = 3, lastEpochDay = 101), SongOpens(count = 3, dayCount = 2, lastEpochDay = 100).counted(today = 101))
    }

    @Test
    fun `a clock moved backwards counts another day and never takes one away`() {
        assertEquals(SongOpens(count = 4, dayCount = 3, lastEpochDay = 90), SongOpens(count = 3, dayCount = 2, lastEpochDay = 100).counted(today = 90))
    }

    @Test
    fun `nothing more is counted once the opens are enough`() {
        val enough = SongOpens(count = 10, dayCount = 3, lastEpochDay = 100)
        assertEquals(enough, enough.counted(today = 105))
    }

    @Test
    fun `ten opens on three days are enough and one fewer of either is not`() {
        assertTrue(SongOpens(count = 10, dayCount = 3).areEnough)
        assertFalse(SongOpens(count = 9, dayCount = 3).areEnough)
        assertFalse(SongOpens(count = 40, dayCount = 2).areEnough)
    }

    @Test
    fun `the demo's songs are not the user's own`() {
        val names = listOf("traditional_american-house_of_the_rising_sun.cho", "traditional_american-home_on_the_range_my_western_home.cho", "oasis-wonderwall.cho")
        assertEquals(1, ownSongCount(names, DemoLibrary::isDemoSong))
    }

    @Test
    fun `the request is due with five songs of their own and enough opens until it has been shown`() {
        val enough = SongOpens(count = 10, dayCount = 3)
        assertTrue(isSpreadTheWordDue(ownSongCount = 5, songOpens = enough, isShown = false))
        assertFalse(isSpreadTheWordDue(ownSongCount = 4, songOpens = enough, isShown = false))
        assertFalse(isSpreadTheWordDue(ownSongCount = 5, songOpens = SongOpens(count = 9, dayCount = 3), isShown = false))
        assertFalse(isSpreadTheWordDue(ownSongCount = 5, songOpens = enough, isShown = true))
    }

    @Test
    fun `the request waits for every dialog and import and question in its way`() {
        fun canShow(
            hasDialog: Boolean = false,
            isImporting: Boolean = false,
            hasImportReport: Boolean = false,
            queuedImportCount: Int = 0,
            isOnboardingPending: Boolean = false,
            isWhatsNewOwed: Boolean = false,
            areSyncDeletionsAwaitingAnswer: Boolean = false,
        ) = canShowSpreadTheWord(
            hasDialog = hasDialog,
            isImporting = isImporting,
            hasImportReport = hasImportReport,
            queuedImportCount = queuedImportCount,
            isOnboardingPending = isOnboardingPending,
            isWhatsNewOwed = isWhatsNewOwed,
            areSyncDeletionsAwaitingAnswer = areSyncDeletionsAwaitingAnswer,
        )
        val cases = mapOf(
            "nothing" to canShow(),
            "a dialog" to canShow(hasDialog = true),
            "an import running" to canShow(isImporting = true),
            "an import under review" to canShow(hasImportReport = true),
            "a batch still queued" to canShow(queuedImportCount = 1),
            "the onboarding owed" to canShow(isOnboardingPending = true),
            "What's new owed" to canShow(isWhatsNewOwed = true),
            "sync waiting for an answer about deletions" to canShow(areSyncDeletionsAwaitingAnswer = true),
        )
        assertEquals(mapOf("nothing" to true), cases.filterValues { it })
    }
}
