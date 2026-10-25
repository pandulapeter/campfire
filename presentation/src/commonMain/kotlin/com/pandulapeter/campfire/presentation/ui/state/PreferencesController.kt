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

import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.tagKey
import com.pandulapeter.campfire.domain.api.preferences.GetUserPreferencesUseCase
import com.pandulapeter.campfire.domain.api.preferences.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.ui.instrument.withInstrument
import com.pandulapeter.campfire.presentation.ui.instrument.withTuning
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import com.pandulapeter.campfire.presentation.ui.platform.LibraryPersistence
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The user's preferences as the app reads them and every switch that writes one. Each write is applied to what the
 * repository holds when it runs ([UpdateUserPreferencesUseCase]) rather than to [userPreferences], which may be a few
 * hops behind it.
 *
 * @param requestPersistence Asks the platform to keep the library, see [libraryPersistence].
 */
internal class PreferencesController(
    private val scope: CoroutineScope,
    getUserPreferences: GetUserPreferencesUseCase,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,
    private val requestPersistence: suspend () -> LibraryPersistence,
) {

    /**
     * Read straight from its own repository rather than out of the library's `screenData`, which only has anything once every
     * source has been read: the theme and the language come from here, and waiting for a scan of the whole song
     * library would leave the app in the system's theme and language for as long as that takes. Both states below
     * are derived from this one, so that they can never disagree about whether the read has happened.
     */
    val userPreferencesState: StateFlow<DataState<UserPreferences>> = getUserPreferences().asState(scope, DataState.Loading(null))

    val userPreferences = userPreferencesState.map { it.data }.asState(scope, null)
    /**
     * False only for as long as the preferences have not been read yet, which is what the app waits for before it
     * draws anything: they decide the palette it is drawn in and the language it is written in, and a frame drawn
     * before they arrive is a frame of the system's guess at both.
     *
     * Read rather than read *successfully*: a read that failed has no answer left to wait for, and the app has to
     * open in the defaults rather than not at all.
     *
     * It only ever turns on, because what it gates is the whole of `CampfireContent`: every remembered thing under
     * it — the scroll position of each list, the state Navigation 3 saved for each entry, the text being edited —
     * is gone the moment this goes false, and the app comes back with the user somewhere they never navigated to.
     * Nothing that happens after the first read is a reason to put the launch screen back up.
     */
    val arePreferencesLoaded = userPreferencesState
        .runningFold(false) { hasBeenRead, state -> hasBeenRead || state !is DataState.Loading }
        .asState(scope, false)

    /**
     * The one preference enough screens ask about to be worth a state of its own: every list, menu, sheet and app
     * bar in the app has something it takes away. Off while the songs are switched off, since its switch goes with
     * them: the library actions of Settings would otherwise stay disabled with nothing on screen saying why.
     */
    val isPerformanceModeEnabled = userPreferences
        .map { it?.isPerformanceModeEnabled == true && it.areSongsEnabled }
        .asState(scope, false)

    /** Whether the app has songs at all, which the import asks before it opens one, see [UserPreferences.areSongsEnabled]. */
    val areSongsEnabled = userPreferences.map { it?.areSongsEnabled != false }.asState(scope, true)

    /** The top level screens the navigation chrome offers, which are only those of the features switched on. */
    val topLevelDestinations = userPreferences
        .map { CampfireDestination.TopLevel.entries(FeatureSwitches.of(it)) }
        .asState(scope, CampfireDestination.TopLevel.entries)

    /**
     * Whether the platform has promised to keep the library, null until it has answered. Asked for here, as early as
     * there is anything to ask from, and never insisted on: on the web this is what stops the browser from evicting
     * the library when the device runs short of space, and everywhere else the answer is a foregone conclusion.
     *
     * A state of the view model rather than something the settings screen asks for as it opens, for the reason every
     * other state here is eager: an answer that arrives a frame after the screen does is a row appearing in the
     * middle of the transition the screen is entering with.
     */
    val libraryPersistence = flow<LibraryPersistence?> { emit(requestPersistence()) }.asState(scope, null)

    init {
        // The chord table of the player's tuning is read off the main thread as soon as the preferences name it, and
        // again for each tuning picked later. The first song opened builds its first page during composition, on the
        // main thread, and would otherwise read the table there: thousands of shapes, frames dropped in its opening
        // transition on a slow phone. On the web the default dispatcher is the page's one thread, so there it only
        // moves the read to while the library is still being read, before any song can be opened.
        scope.launch {
            userPreferences
                .map { preferences -> preferences?.takeIf { it.areChordsEnabled && it.areChordDiagramsEnabled }?.chordTuning() }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { withContext(Dispatchers.Default) { ChordVoicings.prepare(it) } }
        }
    }

    fun setPerformanceModeEnabled(value: Boolean) = changeUserPreferences { copy(isPerformanceModeEnabled = value) }

    fun setChordsEnabled(value: Boolean) = changeUserPreferences { copy(areChordsEnabled = value) }

    fun setChordDiagramsEnabled(value: Boolean) = changeUserPreferences { copy(areChordDiagramsEnabled = value) }

    /** The onboarding's chords card, which stands for the chord diagrams too; one write, so neither change is lost. */
    fun setChordsWithDiagramsEnabled(value: Boolean) = changeUserPreferences { copy(areChordsEnabled = value, areChordDiagramsEnabled = value) }

    fun setInstrument(value: UserPreferences.Instrument) = changeUserPreferences { withInstrument(value) }

    fun setTuning(tuning: InstrumentTuning) = changeUserPreferences { withTuning(tuning) }

    /**
     * Stores [shape] as the player's own for the chord [chordId] under [voicingKey] (a tuning's
     * [ChordTuning.voicingKey]), in every song, or forgets their choice where it is null. Written at once, as the page's
     * own steppers write.
     */
    fun setChordVoicing(voicingKey: String, chordId: String, shape: String?) = changeUserPreferences {
        val shapes = chordVoicings[voicingKey].orEmpty().let { if (shape == null) it - chordId else it + (chordId to shape) }
        copy(chordVoicings = if (shapes.isEmpty()) chordVoicings - voicingKey else chordVoicings + (voicingKey to shapes))
    }

    fun setSongsEnabled(value: Boolean) = changeUserPreferences { copy(areSongsEnabled = value) }

    fun setSetlistsEnabled(value: Boolean) = changeUserPreferences { copy(areSetlistsEnabled = value) }

    fun setMetronomeEnabled(value: Boolean) = changeUserPreferences { copy(isMetronomeEnabled = value) }

    fun setTunerEnabled(value: Boolean) = changeUserPreferences { copy(isTunerEnabled = value) }

    fun setChordDegreesEnabled(value: Boolean) = changeUserPreferences { copy(areChordDegreesEnabled = value) }

    fun setScalesEnabled(value: Boolean) = changeUserPreferences { copy(areScalesEnabled = value) }

    fun setScaleSuggestionsEnabled(value: Boolean) = changeUserPreferences { copy(areScaleSuggestionsEnabled = value) }

    /** The onboarding's scales card, which stands for the scale suggestions too; one write, so neither change is lost. */
    fun setScalesWithSuggestionsEnabled(value: Boolean) = changeUserPreferences { copy(areScalesEnabled = value, areScaleSuggestionsEnabled = value) }

    /**
     * Folds or unfolds one section of a song (or one tab or grid inside it, or its Chords or Scales section), [key] being the name the song details
     * screen gives it. One set per song, wherever it is opened from, and kept in the preferences rather than in a
     * setlist, since it is how one reader reads the song rather than how the band plays it. It toggles what the
     * preferences hold when the change runs, which already has the previous tap in it (see `PlayingOverrides.changeTransposition`).
     */
    fun toggleSectionFold(songFileName: String, key: String) = changeUserPreferences {
        val folded = foldedSections[songFileName].orEmpty().let { if (key in it) it - key else it + key }
        copy(foldedSections = if (folded.isEmpty()) foldedSections - songFileName else foldedSections + (songFileName to folded))
    }

    fun setSortingMode(value: UserPreferences.SortingMode) = changeUserPreferences { copy(sortingMode = value) }

    fun setTagSortingMode(value: UserPreferences.LabelSortingMode) = changeUserPreferences { copy(tagSortingMode = value) }

    /** [colorId] null puts the tag back to the default, which is no entry rather than an entry saying so. */
    fun setTagColor(tag: String, colorId: String?) = changeUserPreferences {
        val key = tagKey(tag)
        copy(tagColors = if (colorId == null) tagColors - key else tagColors + (key to colorId))
    }

    fun setLanguageSortingMode(value: UserPreferences.LabelSortingMode) = changeUserPreferences { copy(languageSortingMode = value) }

    fun setUiMode(value: UserPreferences.UiMode) = changeUserPreferences { copy(uiMode = value) }

    fun setThemeColor(value: UserPreferences.ThemeColor) = changeUserPreferences { copy(themeColor = value) }

    fun setBackgroundWarmth(value: Int) = changeUserPreferences { copy(backgroundWarmth = value) }

    fun setAppIconThemed(value: Boolean) = changeUserPreferences { copy(isAppIconThemed = value) }

    fun setCoverArtEnabled(value: Boolean) = changeUserPreferences { copy(isCoverArtEnabled = value) }

    fun setSongDetailsLookupEnabled(value: Boolean) = changeUserPreferences { copy(isSongDetailsLookupEnabled = value) }

    fun setSongCardDetails(value: UserPreferences.CardDetails) = changeUserPreferences { copy(songCardDetails = value) }

    fun setSetlistCardDetails(value: UserPreferences.CardDetails) = changeUserPreferences { copy(setlistCardDetails = value) }

    fun setSectionNumberingEnabled(value: Boolean) = changeUserPreferences { copy(shouldNumberSections = value) }

    fun setLanguage(value: UserPreferences.Language) = changeUserPreferences { copy(language = value) }

    fun setAccidentals(value: UserPreferences.Accidentals) = changeUserPreferences { copy(chordSpelling = chordSpelling.copy(accidentals = value)) }

    fun setNotation(notation: UserPreferences.Notation) = changeUserPreferences { copy(chordSpelling = chordSpelling.copy(notation = notation)) }

    fun changeUserPreferences(change: UserPreferences.() -> UserPreferences) {
        scope.launch { updateUserPreferences { it.change() } }
    }
}
