/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches

// Which rows of the Preferences are there at all under the Features switches. A Feature switched off takes every
// mention of itself out of the app, so a row that only changes what a Feature off would show is hidden, never
// disabled; a row that waits for another preference (the chords, the diagrams, a numbering) stays, disabled, since
// what it is set to is what that preference brings back.

/** Whether the Dock section's Metronome switch is there: only with the Metronome feature on. */
internal fun isDockMetronomeSwitchShown(userPreferences: UserPreferences?) = FeatureSwitches.of(userPreferences).isMetronomeEnabled

/** Whether the Dock section's Tuner switch is there: only with the Tuner feature on (enabled by [isDockTunerSwitchEnabled]). */
internal fun isDockTunerSwitchShown(userPreferences: UserPreferences?) = FeatureSwitches.of(userPreferences).isTunerEnabled

/** Whether the Dock section's Scale suggestions switch is there: only with the Scales feature on, whose sheet they open. */
internal fun isScaleSuggestionsSwitchShown(userPreferences: UserPreferences?) = FeatureSwitches.of(userPreferences).areScalesEnabled

/**
 * Whether the Chord degrees switch is there: wherever a diagram it labels could be drawn, a song's (the Songs feature)
 * or a scale sheet's chords (the Chords and the Scales features both).
 */
internal fun isChordDegreesSwitchShown(userPreferences: UserPreferences?) = FeatureSwitches.of(userPreferences).let {
    it.areSongsEnabled || (it.isChordLibraryEnabled && it.areScalesEnabled)
}

/**
 * Whether the notation and the accidentals are there: wherever anything could name a chord or a key, a song (the Songs
 * feature), the Chords tab, or the Scales tab. With none of them the whole Names section goes, its heading too.
 */
internal fun areChordSpellingRowsShown(userPreferences: UserPreferences?) = FeatureSwitches.of(userPreferences).let {
    it.areSongsEnabled || it.isChordLibraryEnabled || it.areScalesEnabled
}

/** Whether the rows about a song's capo (Chord names with a capo, Capo keeps the key) are there: only with the songs. */
internal fun areSongCapoRowsShown(userPreferences: UserPreferences?) = FeatureSwitches.of(userPreferences).areSongsEnabled
