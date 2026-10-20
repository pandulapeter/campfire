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

import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.data.model.preferences.TunerSettings
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.api.preferences.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.scales.SCALE_NOTE_LEAD_MILLIS
import com.pandulapeter.campfire.presentation.ui.instrument.courses
import com.pandulapeter.campfire.presentation.ui.state.DebouncedPreference
import com.pandulapeter.campfire.presentation.ui.state.asState
import com.pandulapeter.campfire.tuner.api.Pitch
import com.pandulapeter.campfire.tuner.api.Tuner
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import com.pandulapeter.campfire.tuner.api.model.StringCourses
import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import com.pandulapeter.campfire.tuner.api.model.TunerListening
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The tuner: its settings, listening while a tuner screen asks for it, the reference tones, and the scale sheet's run of
 * them. Nothing of it outlives
 * the screen it is used on - the view model stops it as that screen leaves the back stack or the sheet closes, and the
 * screen as the app goes out of sight.
 *
 * @param writeDelayMillis How long the settings have to hold still before they are written to the preferences.
 */
internal class TunerController(
    private val scope: CoroutineScope,
    private val tuner: Tuner,
    private val userPreferences: StateFlow<UserPreferences?>,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,
    private val writeDelayMillis: Long,
) {

    /** Saved like the metronome's settings: a held stepper is a new reference pitch several times a second. */
    val tunerSettingsPreference = DebouncedPreference<TunerSettings> { copy(tunerSettings = it) }

    val tunerSettings = combine(userPreferences, tunerSettingsPreference.pending) { userPreferences, pending ->
        pending ?: userPreferences?.tunerSettings ?: TunerSettings()
    }.asState(scope, TunerSettings())

    /**
     * What the tuner listens for: the instrument in the player's hands, which the instrument sheet writes into the
     * preferences, and the tuner's own settings, so that a pick in the sheet retunes a tuner that is listening at once.
     */
    val tunerConfig = combine(userPreferences, tunerSettings) { userPreferences, settings ->
        tunerConfigOf(settings, userPreferences.listenedTuning, userPreferences.listenedCourses)
    }.asState(scope, tunerConfigOf(tunerSettings.value, userPreferences.value.listenedTuning, userPreferences.value.listenedCourses))

    val tunerState = tuner.state

    /** See [TunerSettings.hasTurnedOnMicrophone]. */
    val hasTurnedOnMicrophone = tunerSettings.map { it.hasTurnedOnMicrophone }.asState(scope, tunerSettings.value.hasTurnedOnMicrophone)

    private val _tunedNotes = MutableStateFlow(emptySet<Int>())

    /** The notes heard in tune since the microphone was opened, which the strings row ticks off, see [tunedNotesAfter]. */
    val tunedNotes = _tunedNotes.asStateFlow()

    private val _playingScaleNote = MutableStateFlow<Int?>(null)

    /** Which of the scale sheet's notes sounds now, as an index into those [toggleScale] plays, or null between runs. */
    val playingScaleNote = _playingScaleNote.asStateFlow()

    private var scaleJob: Job? = null

    private val config get() = tunerConfig.value

    /** Whether the tuner on its way to the screen was opened by a tap, see [markOpenedByTap]. */
    private var isOpenedByTap = false

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

    /**
     * Notes that the tuner about to be shown was opened by a tap on the Tuner tab or on a song's tuner button, which is
     * the player asking to tune: its page then asks for a microphone never asked about at once, rather than after a
     * second tap on its notice. A tuner shown without one - restored with the app, the web's address or its Back - waits
     * for that tap, since a system prompt nobody asked for is easily refused, and iOS and Android only ask so often.
     */
    fun markOpenedByTap() {
        isOpenedByTap = true
    }

    /** Whether the tuner now on screen was opened by a tap, answered once, so that its page asks only as it opens. */
    fun consumeOpenedByTap() = isOpenedByTap.also { isOpenedByTap = false }

    /** Plays [note], or stops it where it is the one sounding. */
    fun toggleTunerTone(note: Int) {
        if (tuner.state.value.tone == note) tuner.stopTone() else tuner.playTone(note, tunerSettings.value.referencePitch)
    }

    /**
     * Plays [notes] (MIDI note numbers) one after the other as reference tones at the tuner's reference pitch, each
     * replacing the last, or stops them where they are already playing.
     *
     * Each note is timed from the moment the tuner says it sounds, not from the moment it was asked for: the first tone
     * of a session opens the platform's output on the engine's thread, which can take longer than a note lasts, and a
     * clock started at the request would cut the first note short and find the second one queued behind it. A note the
     * output never starts (focus refused) is waited for only so long, then the run goes on.
     *
     * [playingScaleNote] moves on first and the note follows [SCALE_NOTE_LEAD_MILLIS] later, the time the sheet's circle
     * takes to reach its degree, so that the circle arriving reads as what plays the note. The lead comes out of the
     * note before, so the tempo is the same.
     */
    fun toggleScale(notes: List<Int>) {
        if (_playingScaleNote.value != null) return stopScale()
        // Set before the run starts, so that a second tap in the same frame already finds a scale to stop.
        _playingScaleNote.value = 0
        scaleJob = scope.launch {
            notes.forEachIndexed { index, note ->
                _playingScaleNote.value = index
                delay(SCALE_NOTE_LEAD_MILLIS.toLong())
                tuner.playTone(note, tunerSettings.value.referencePitch)
                withTimeoutOrNull(SCALE_NOTE_START_TIMEOUT_MILLIS) { tuner.state.first { it.tone == note } }
                delay(if (index == notes.lastIndex) SCALE_LAST_NOTE_MILLIS else SCALE_NOTE_MILLIS - SCALE_NOTE_LEAD_MILLIS)
            }
            tuner.stopTone()
            _playingScaleNote.value = null
        }
    }

    /**
     * Ends a scale that is playing. The run and this share the scope's one thread, so a cancelled run never gets to play
     * another note after the tone is stopped here.
     */
    fun stopScale() {
        if (_playingScaleNote.value == null) return
        scaleJob?.cancel()
        scaleJob = null
        tuner.stopTone()
        _playingScaleNote.value = null
    }

    fun stopTuner() {
        stopScale()
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
     * Follows the settings and the instrument with what is listening and sounding: a listening tuner reads against the new
     * strings and pitch from its next window, a tone of strings that are no longer chosen ends, and one still chosen moves
     * to the new reference pitch.
     */
    fun startFollowingSettings() = scope.launch {
        var previous: TunerConfig? = null
        tunerConfig.collect { config ->
            val last = previous
            previous = config
            if (last == null || last == config) return@collect
            if (tuner.state.value.listening !is TunerListening.Stopped) tuner.update(config)
            // The strings ticked off belong to the instrument they were tuned for.
            if (last.strings != config.strings) _tunedNotes.value = emptySet()
            val tone = tuner.state.value.tone ?: return@collect
            when {
                last.strings != config.strings -> tuner.stopTone()
                last.referencePitch != config.referencePitch -> tuner.playTone(tone, config.referencePitch)
            }
        }
    }

    /**
     * Tells the engine whether a screen that plays its tones is showing, see `Tuner.setStartable`: the Tuner tab on top of
     * [backStack], or the tuner sheet as [visibleDialog], while the tuner is switched on, and the scale sheet, whose notes
     * are its tones whether the tuner is switched on or not.
     */
    fun startStartableRule(backStack: List<CampfireDestination>, visibleDialog: StateFlow<DialogType?>) = scope.launch {
        combine(
            snapshotFlow { backStack.lastOrNull() },
            visibleDialog,
            userPreferences.map { it?.isTunerEnabled != false },
        ) { top, dialog, isTunerEnabled ->
            dialog is DialogType.Scale || isTunerEnabled && (top == CampfireDestination.Tuner || dialog is DialogType.Tuner)
        }
            .distinctUntilChanged()
            .collect(tuner::setStartable)
    }
}

private const val SCALE_NOTE_MILLIS = 250L
private const val SCALE_LAST_NOTE_MILLIS = 600L
private const val SCALE_NOTE_START_TIMEOUT_MILLIS = 2_000L

/** What the instrument in the player's hands is tuned to, a fresh installation's guitar before the preferences are read. */
private val UserPreferences?.listenedTuning get() = if (this == null) InstrumentTuning.GUITAR else instrumentTuning

/** How the instrument in the player's hands strings its tuning, single strings before the preferences are read. */
private val UserPreferences?.listenedCourses get() = this?.instrument?.courses ?: StringCourses.SINGLE

/**
 * The tuner's own model of what it listens for: the strings of [instrumentTuning], both of each course where [courses]
 * pairs them, or the nearest note (a null tuning) where the instrument has no strings to tune (the keyboard, Other).
 */
internal fun tunerConfigOf(settings: TunerSettings, instrumentTuning: InstrumentTuning?, courses: StringCourses) = TunerConfig(
    referencePitch = settings.referencePitch.coerceIn(Pitch.REFERENCE_PITCH_RANGE),
    tuning = instrumentTuning,
    courses = courses,
)
