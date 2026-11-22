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

/**
 * Which of a song's sections the preferences draw, decided once for the song details screen and the PDF, so that a page
 * and its printout never disagree on what a song shows.
 *
 * @property areDiagramsShown Whether the Chords section is drawn: with the chords and the Chord diagrams switch, and
 *   never before the preferences are read, since the diagrams are drawn in the player's tuning.
 * @property areDegreesShown Whether each diagram is labelled with its step of the key, which needs a diagram to label.
 * @property areScaleSuggestionsShown Whether the Scales section is drawn: with the chords, since the scales are read off
 *   them, and with the Scale suggestions switch, which the Scales one takes with it ([FeatureSwitches]).
 */
internal data class SongSections(
    val areDiagramsShown: Boolean,
    val areDegreesShown: Boolean,
    val areScaleSuggestionsShown: Boolean,
) {

    companion object {

        /** The sections as [preferences] have them. */
        fun of(preferences: UserPreferences?): SongSections {
            val areDiagramsShown = preferences != null && preferences.areChordsEnabled && preferences.areChordDiagramsEnabled
            return SongSections(
                areDiagramsShown = areDiagramsShown,
                areDegreesShown = areDiagramsShown && preferences.areChordDegreesEnabled,
                areScaleSuggestionsShown = preferences?.areChordsEnabled != false && FeatureSwitches.of(preferences).areScaleSuggestionsEnabled,
            )
        }
    }
}
