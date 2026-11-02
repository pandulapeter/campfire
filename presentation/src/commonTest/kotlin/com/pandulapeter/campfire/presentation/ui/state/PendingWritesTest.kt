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

import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.playing.PendingOverrides
import com.pandulapeter.campfire.presentation.ui.playing.SongOverrides
import com.pandulapeter.campfire.presentation.ui.playing.SongPlace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingWritesTest {

    private var stored = preferences()
    private val events = mutableListOf<String>()
    private val write: PreferencesWrite = { update ->
        events += "preferences"
        stored = update(stored)
    }

    @Test
    fun `a preference made by it saves once it holds still`() = runTest {
        val fontScale = PendingWrites(backgroundScope, DELAY, write).preference<Float> { copy(fontScale = it) }
        fontScale.set(1.3f)
        runCurrent()
        assertEquals(emptyList(), events)
        advanceTimeBy(DELAY + 1)
        assertEquals(listOf("preferences"), events)
        assertEquals(1.3f, stored.fontScale)
        assertNull(fontScale.pending.value)
    }

    @Test
    fun `flushAll writes every waiting preference in one write and the overrides after it`() = runTest {
        val pendingWrites = PendingWrites(backgroundScope, DELAY, write)
        val fontScale = pendingWrites.preference<Float> { copy(fontScale = it) }
        val numbering = pendingWrites.preference<Boolean> { copy(shouldNumberSections = it) }
        val tempos = PendingOverrides(
            scope = backgroundScope,
            pendingWrites = pendingWrites,
            stored = MutableStateFlow(SongOverrides<Int>()),
            name = "tempo",
            write = { _, value ->
                events += "override $value"
                true
            },
            onFailed = {},
        )
        fontScale.set(1.4f)
        numbering.set(false)
        tempos.set(SongPlace("a.cho", null), 90)
        pendingWrites.flushAll()
        assertEquals(listOf("preferences", "override 90"), events)
        assertEquals(1.4f, stored.fontScale)
        assertEquals(false, stored.shouldNumberSections)
        advanceTimeBy(DELAY * 2)
        assertEquals(listOf("preferences", "override 90"), events)
    }

    @Test
    fun `a value set after the flush was read is kept waiting`() = runTest {
        val pendingWrites = PendingWrites(backgroundScope, DELAY) { update ->
            fontScale.set(1.5f)
            write(update)
        }
        fontScale = pendingWrites.preference { copy(fontScale = it) }
        val numbering = pendingWrites.preference<Boolean> { copy(shouldNumberSections = it) }
        fontScale.set(1.4f)
        numbering.set(false)
        pendingWrites.flushAll()
        assertEquals(1.4f, stored.fontScale)
        assertEquals(1.5f, fontScale.pending.value)
        assertNull(numbering.pending.value)
    }

    private lateinit var fontScale: DebouncedPreference<Float>

    private companion object {
        const val DELAY = 500L

        fun preferences() = UserPreferences(
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
            language = AppLanguage.SYSTEM_DEFAULT,
            chordSpelling = UserPreferences.ChordSpelling.Default,
            transpositions = emptyMap(),
            foldedSections = emptyMap(),
            tagMatchMode = UserPreferences.MatchMode.ANY,
            languageMatchMode = UserPreferences.MatchMode.ANY,
            tagSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
            languageSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
        )
    }
}
