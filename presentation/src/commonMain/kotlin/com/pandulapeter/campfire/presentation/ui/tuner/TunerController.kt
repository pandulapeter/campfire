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

import com.pandulapeter.campfire.data.model.domain.TunerSettings
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.useCases.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.ui.state.DebouncedPreference
import com.pandulapeter.campfire.presentation.ui.state.asState
import com.pandulapeter.campfire.tuner.api.Pitch
import com.pandulapeter.campfire.tuner.api.Tuner
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import com.pandulapeter.campfire.tuner.api.model.TunerListening
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The tuner: its settings, listening while a tuner screen asks for it, and the reference tones. Nothing of it outlives
 * the screen it is used on - the view model stops it as that screen leaves the back stack or the sheet closes, and the
 * screen as the app goes out of sight.
 *
 * @param writeDelayMillis How long the settings have to hold still before they are written to the preferences.
 */
internal class TunerController(
    private val scope: CoroutineScope,
    private val tuner: Tuner,
    userPreferences: StateFlow<UserPreferences?>,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,
    private val writeDelayMillis: Long,
) {

    /** Saved like the metronome's settings: a held stepper is a new reference pitch several times a second. */
    val tunerSettingsPreference = DebouncedPreference<TunerSettings> { copy(tunerSettings = it) }

    val tunerSettings = combine(userPreferences, tunerSettingsPreference.pending) { userPreferences, pending ->
        pending ?: userPreferences?.tunerSettings ?: TunerSettings()
    }.asState(scope, TunerSettings())

    val tunerState = tuner.state

    /** See [TunerSettings.hasTurnedOnMicrophone]. */
    val hasTurnedOnMicrophone = tunerSettings.map { it.hasTurnedOnMicrophone }.asState(scope, tunerSettings.value.hasTurnedOnMicrophone)

    private val _tunedNotes = MutableStateFlow(emptySet<Int>())

    /** The notes heard in tune since the microphone was opened, which the strings row ticks off, see [tunedNotesAfter]. */
    val tunedNotes = _tunedNotes.asStateFlow()

    private val config get() = tunerSettings.value.toConfig()

    /** Listens while a tuner screen is showing and the microphone may be used, see `TunerScreen`. */
    fun setTunerListening(isListening: Boolean) {
        if (isListening) {
            tuner.listen(config)
        } else {
            stopTuner()
        }
    }

    /** The page's button: what asks, on the platforms where opening the input is what asks. */
    fun requestMicrophone() {
        if (!tunerSettings.value.hasTurnedOnMicrophone) updateTunerSettings { copy(hasTurnedOnMicrophone = true) }
        tuner.listen(config)
    }

    /** Plays [note], or stops it where it is the one sounding. */
    fun toggleTunerTone(note: Int) {
        if (tuner.state.value.tone == note) tuner.stopTone() else tuner.playTone(note, tunerSettings.value.referencePitch)
    }

    fun stopTuner() {
        tuner.stopListening()
        tuner.stopTone()
    }

    fun updateTunerSettings(change: TunerSettings.() -> TunerSettings) = tunerSettingsPreference.update { (it ?: tunerSettings.value).change() }

    /** Follows what is heard with [tunedNotes]. */
    fun startTrackingTunedNotes() = scope.launch {
        tuner.state.collect { state -> _tunedNotes.update { tunedNotesAfter(it, state.listening) } }
    }

    /** Writes the settings once they have held still, see [tunerSettingsPreference]. */
    fun startSettingsWriter() = tunerSettingsPreference.start(scope, writeDelayMillis, updateUserPreferences::invoke)

    /**
     * Follows the settings with what is listening and sounding: a listening tuner reads against the new instrument and
     * pitch from its next window, a tone of an instrument that is no longer chosen ends, and one still chosen moves to
     * the new reference pitch.
     */
    fun startFollowingSettings() = scope.launch {
        var previous: TunerSettings? = null
        tunerSettings.collect { settings ->
            val last = previous
            previous = settings
            if (last == null || last == settings) return@collect
            if (tuner.state.value.listening !is TunerListening.Stopped) tuner.update(settings.toConfig())
            // The strings ticked off belong to the instrument they were tuned for.
            if (last.instrumentId != settings.instrumentId) _tunedNotes.value = emptySet()
            val tone = tuner.state.value.tone ?: return@collect
            when {
                last.instrumentId != settings.instrumentId -> tuner.stopTone()
                last.referencePitch != settings.referencePitch -> tuner.playTone(tone, settings.referencePitch)
            }
        }
    }

    /** Tells the engine whether a tuner screen is showing, see `Tuner.setStartable`. */
    fun startStartableRule(isTunerShown: Flow<Boolean>) = scope.launch { isTunerShown.distinctUntilChanged().collect(tuner::setStartable) }
}

/** The tuner's own model of the stored settings: an instrument this version does not know is chromatic. */
internal fun TunerSettings.toConfig() = TunerConfig(
    referencePitch = referencePitch.coerceIn(Pitch.REFERENCE_PITCH_RANGE),
    tuning = InstrumentTuning.fromId(instrumentId),
)
