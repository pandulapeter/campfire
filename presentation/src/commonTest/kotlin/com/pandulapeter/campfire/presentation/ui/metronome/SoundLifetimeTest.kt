/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.SavedStateHandle
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.presentation.ui.chords.ChordLibraryState
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.Navigator
import com.pandulapeter.campfire.presentation.ui.navigation.OnboardingStep
import com.pandulapeter.campfire.presentation.ui.scales.ScaleLibraryState
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SoundLifetimeTest {

    private val metronome = RecordingMetronome()
    private var tunerStops = 0
    private var scaleStops = 0

    private fun soundLifetime(scope: CoroutineScope): Pair<Navigator, DialogHost> {
        val dialogHost = DialogHost(scope)
        val soundLifetime = SoundLifetime(
            metronome = metronome,
            stopTuner = { tunerStops++ },
            stopScale = { scaleStops++ },
            dismissTunerSheet = { dialogHost.dismissSheet(DialogType.Tuner) },
        )
        dialogHost.addBeforeDialogChange(soundLifetime::onBeforeDialogChange)
        val navigator = Navigator(
            savedStateStore = SavedStateStore(SavedStateHandle(), scope),
            dialogHost = dialogHost,
            songsSearch = { SearchState() },
            setlistsSearch = { SearchState() },
            importReportSearch = { SearchState(isInitiallyOpen = true) },
            chordLibrary = { ChordLibraryState(keyIndex = mutableIntStateOf(0)) },
            scaleLibrary = { ScaleLibraryState(keyIndex = mutableIntStateOf(0)) },
            onboardingSteps = { listOf(OnboardingStep.WELCOME) },
            topLevelDestinations = { CampfireDestination.TopLevel.entries },
            userPreferences = { null as UserPreferences? },
            hasUnsavedEditorText = { false },
            hasUnsavedEditorChanges = { false },
            isSetlistReordering = { false },
            endSetlistReordering = {},
            isSongSelecting = { false },
            endSongSelection = {},
            onTransitionEnded = {},
        )
        navigator.addOnBackStackChanged(soundLifetime::onBackStackChanged)
        return navigator to dialogHost
    }

    @Test
    fun `leaving the song a click was started on stops it`() = runTest {
        val (navigator, _) = soundLifetime(backgroundScope)
        navigator.updateBackStack { add(song()) }
        val stopsWhileOpen = metronome.stops
        navigator.updateBackStack { removeAt(lastIndex) }
        assertEquals(stopsWhileOpen + 1, metronome.stops)
    }

    @Test
    fun `a sheet over the song leaves the click going`() = runTest {
        val (navigator, dialogHost) = soundLifetime(backgroundScope)
        navigator.updateBackStack { add(song()) }
        val stopsWhileOpen = metronome.stops
        dialogHost.showDialog(DialogType.Instrument)
        dialogHost.dismissDialog()
        assertEquals(stopsWhileOpen, metronome.stops)
    }

    @Test
    fun `opening the export screen or the tuner sheet stops the click`() = runTest {
        val (navigator, dialogHost) = soundLifetime(backgroundScope)
        navigator.updateBackStack { add(song()) }
        val stopsWhileOpen = metronome.stops
        dialogHost.showDialog(DialogType.Export())
        assertEquals(stopsWhileOpen + 1, metronome.stops)
        dialogHost.dismissDialog()
        dialogHost.showDialog(DialogType.Tuner)
        assertEquals(stopsWhileOpen + 2, metronome.stops)
    }

    @Test
    fun `another song arriving on top stops the tuner and closes its sheet`() = runTest {
        val (navigator, dialogHost) = soundLifetime(backgroundScope)
        navigator.updateBackStack { add(song()) }
        dialogHost.showDialog(DialogType.Tuner)
        val tunerStopsWhileOpen = tunerStops
        navigator.updateBackStack { add(song()) }
        assertEquals(null, dialogHost.visibleDialog.value)
        assertEquals(true, tunerStops > tunerStopsWhileOpen)
    }

    @Test
    fun `covering the scale sheet with a chord's sheet stops the scale`() = runTest {
        val (_, dialogHost) = soundLifetime(backgroundScope)
        dialogHost.showDialog(DialogType.Scale(scaleId = "major", rootName = "C"))
        assertEquals(0, scaleStops)
        dialogHost.showDialog(DialogType.ChordReference(SongChord(name = "C", chord = Chord(root = 0, intervals = listOf(0, 4, 7)))))
        assertEquals(1, scaleStops)
    }

    private fun song() = CampfireDestination.SongDetails(songFileNames = listOf("song.cho"), setlistFileName = null, initialIndex = 0)

    /** A metronome that only counts how often it was stopped. */
    private class RecordingMetronome : Metronome {
        var stops = 0
        override val playback = MutableStateFlow<MetronomePlayback>(MetronomePlayback.Stopped(reason = null))
        override val beats = MutableSharedFlow<MetronomeBeat>()
        override fun start(pattern: MetronomePattern) = Unit
        override fun update(pattern: MetronomePattern, fromNextBar: Boolean) = Unit
        override fun preview(sound: MetronomeSound, level: BeatLevel) = Unit
        override fun stop() {
            stops++
        }
        override fun setStartable(isStartable: Boolean) = Unit
    }
}
