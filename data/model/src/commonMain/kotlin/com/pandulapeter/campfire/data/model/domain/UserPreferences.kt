/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

data class UserPreferences(
    val shouldShowSongsWithoutChords: Boolean,
    val isLyricsOnlyModeEnabled: Boolean,
    val isHorizontalSectionFlowEnabled: Boolean, // Whether the song sections are read across the columns (then downwards) instead of column by column.
    val fontScale: Float, // Multiplier applied to the text size of the song details screen, 1 being the default.
    val sortingMode: SortingMode,
    val uiMode: UiMode,
    val language: Language,
    val accidentals: Accidentals, // How the notes between the white keys are spelled when a song is displayed.
    /** Song file name to semitones, for songs opened from the library rather than from a setlist. */
    val transpositions: Map<String, Int>
) {

    enum class SortingMode(val id: String) {
        BY_TITLE("by_title"),
        BY_ARTIST("by_artist")
    }

    enum class UiMode(val id: String) {
        LIGHT("light"),
        DARK("dark"),
        SYSTEM_DEFAULT("system_default")
    }

    /**
     * The spelling of the black keys the viewer writes. [ORIGINAL] leaves it to the song — its key, or the accidentals
     * its chords are already written with — which is what a reader who never thought about it wants; the other two
     * are for the one who always reads the same one, and hold even for a song that is not transposed at all.
     */
    enum class Accidentals(val id: String) {
        ORIGINAL("original"),
        FLATS("flats"),
        SHARPS("sharps")
    }

    enum class Language(val id: String) {
        ENGLISH("en"),
        HUNGARIAN("hu"),
        SYSTEM_DEFAULT("system_default")
    }
}
