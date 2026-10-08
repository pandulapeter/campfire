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

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedPreferenceTest {

    private var stored = preferences()
    private var writeCount = 0
    private val write: PreferencesWrite = { update ->
        writeCount++
        stored = update(stored)
    }

    @Test
    fun aBurstOfValuesIsWrittenOnceWithTheLastOneAfterTheDelay() = runTest {
        val fontScale = DebouncedPreference<Float> { copy(fontScale = it) }
        fontScale.start(backgroundScope, DELAY, write)
        fontScale.set(1.1f)
        advanceTimeBy(DELAY / 2)
        fontScale.set(1.2f)
        advanceTimeBy(DELAY / 2)
        fontScale.set(1.3f)
        runCurrent()
        assertEquals(0, writeCount)
        advanceTimeBy(DELAY + 1)
        assertEquals(1, writeCount)
        assertEquals(1.3f, stored.fontScale)
        assertNull(fontScale.pending.value)
    }

    @Test
    fun aValueSetWhileAWriteIsInFlightIsKeptAndWrittenNext() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slowWrite: PreferencesWrite = { update ->
            gate.await()
            write(update)
        }
        val fontScale = DebouncedPreference<Float> { copy(fontScale = it) }
        fontScale.start(backgroundScope, DELAY, slowWrite)
        fontScale.set(1.1f)
        advanceTimeBy(DELAY + 1)
        fontScale.set(1.2f)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1.1f, stored.fontScale)
        assertEquals(1.2f, fontScale.pending.value)
        advanceTimeBy(DELAY + 1)
        assertEquals(1.2f, stored.fontScale)
        assertNull(fontScale.pending.value)
    }

    @Test
    fun flushAllWritesEveryWaitingValueInOneCall() = runTest {
        val fontScale = DebouncedPreference<Float> { copy(fontScale = it) }
        val numbering = DebouncedPreference<Boolean> { copy(shouldNumberSections = it) }
        val unused = DebouncedPreference<Boolean> { copy(isCoverArtEnabled = it) }
        fontScale.set(1.4f)
        numbering.set(false)
        DebouncedPreference.flushAll(fontScale, numbering, unused, write = write)
        assertEquals(1, writeCount)
        assertEquals(1.4f, stored.fontScale)
        assertEquals(false, stored.shouldNumberSections)
        assertEquals(true, stored.isCoverArtEnabled)
        assertNull(fontScale.pending.value)
        assertNull(numbering.pending.value)
    }

    @Test
    fun flushAllWritesNothingWhenNothingIsWaiting() = runTest {
        DebouncedPreference.flushAll(DebouncedPreference<Float> { copy(fontScale = it) }, write = write)
        assertEquals(0, writeCount)
    }

    @Test
    fun flushAllLetsGoOnlyOfTheValuesUnchangedSinceItReadThem() = runTest {
        val fontScale = DebouncedPreference<Float> { copy(fontScale = it) }
        val numbering = DebouncedPreference<Boolean> { copy(shouldNumberSections = it) }
        fontScale.set(1.4f)
        numbering.set(false)
        DebouncedPreference.flushAll(fontScale, numbering) { update ->
            fontScale.set(1.5f)
            write(update)
        }
        assertEquals(1.4f, stored.fontScale)
        assertEquals(1.5f, fontScale.pending.value)
        assertNull(numbering.pending.value)
    }

    @Test
    fun aFailingFlushAllIsCaughtAndLeavesTheValuesWaiting() = runTest {
        val fontScale = DebouncedPreference<Float> { copy(fontScale = it) }
        fontScale.set(1.4f)
        DebouncedPreference.flushAll(fontScale) { throw IllegalStateException("The disk is full.") }
        assertEquals(1.4f, fontScale.pending.value)
    }

    @Test
    fun flushWritesTheWaitingValueAtOnce() = runTest {
        val fontScale = DebouncedPreference<Float> { copy(fontScale = it) }
        fontScale.set(1.4f)
        fontScale.flush(write)
        assertEquals(1.4f, stored.fontScale)
        assertNull(fontScale.pending.value)
    }

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
            language = UserPreferences.Language.SYSTEM_DEFAULT,
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
