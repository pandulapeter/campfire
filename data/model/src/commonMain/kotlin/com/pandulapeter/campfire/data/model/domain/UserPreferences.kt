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

    enum class Language(val id: String) {
        ENGLISH("en"),
        HUNGARIAN("hu"),
        SYSTEM_DEFAULT("system_default")
    }
}
