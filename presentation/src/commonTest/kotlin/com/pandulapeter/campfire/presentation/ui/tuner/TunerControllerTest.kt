/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.api.preferences.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.tuner.api.Tuner
import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import com.pandulapeter.campfire.tuner.api.model.TunerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TunerControllerTest {

    @Test
    fun `a scale whose output refuses its first note ends at once`() = runTest {
        val tuner = ScaleTuner(isRefusing = true)
        val controller = controller(tuner)
        controller.toggleScale(listOf(60, 62, 64))
        advanceTimeBy(500)
        assertNull(controller.playingScaleNote.value)
        assertEquals(listOf(60), tuner.requestedNotes)
    }

    @Test
    fun `a scale plays every note when the output starts them`() = runTest {
        val tuner = ScaleTuner(isRefusing = false)
        val controller = controller(tuner)
        controller.toggleScale(listOf(60, 62, 64))
        // The run is in the background scope, which advanceUntilIdle leaves alone; this outlasts it.
        advanceTimeBy(5_000)
        assertEquals(listOf(60, 62, 64), tuner.requestedNotes)
        assertNull(controller.playingScaleNote.value)
    }

    private fun TestScope.controller(tuner: Tuner) = TunerController(
        scope = backgroundScope,
        tuner = tuner,
        userPreferences = MutableStateFlow<UserPreferences?>(null),
        updateUserPreferences = object : UpdateUserPreferencesUseCase {
            override suspend fun invoke(update: (UserPreferences) -> UserPreferences) = Unit
        },
        writeDelayMillis = 0L,
    )

    /** Starts every tone it is asked for at once, or refuses each, as an output that will not play does. */
    private class ScaleTuner(private val isRefusing: Boolean) : Tuner {
        val requestedNotes = mutableListOf<Int>()
        override val state = MutableStateFlow(TunerState())

        override fun playTone(note: Int, referencePitch: Int) {
            requestedNotes += note
            state.update { if (isRefusing) it.copy(tone = null, refusedTones = it.refusedTones + 1) else it.copy(tone = note) }
        }

        override fun stopTone() = state.update { it.copy(tone = null) }

        override fun listen(config: TunerConfig) = Unit

        override fun update(config: TunerConfig) = Unit

        override fun stopListening() = Unit

        override fun setStartable(isStartable: Boolean) = Unit
    }
}
