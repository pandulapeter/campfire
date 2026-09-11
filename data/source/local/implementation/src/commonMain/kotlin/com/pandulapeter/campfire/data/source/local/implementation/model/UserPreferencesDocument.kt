/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.model

import kotlinx.serialization.Serializable

/**
 * The on-disk shape of `preferences.json`. Every field is defaulted, so a document written by an older version keeps
 * loading instead of being discarded; the enums are stored by their stable `id` rather than by ordinal.
 */
@Serializable
internal data class UserPreferencesDocument(
    val isPerformanceModeEnabled: Boolean = false,
    // On by default: a song created in the app starts out as a title and an artist, so hiding songs without chords
    // would hide every new song right after it was made.
    val shouldShowSongsWithoutChords: Boolean = true,
    val isLyricsOnlyModeEnabled: Boolean = false,
    // On by default: reading the sections across the columns means that scrolling never sends the reader back up,
    // which is what a song being played wants.
    val isHorizontalSectionFlowEnabled: Boolean = true,
    val fontScale: Float = 1f,
    val sortingMode: String = "",
    val uiMode: String = "",
    val themeColor: String = "",
    val language: String = "",
    val accidentals: String = "",
    val isGermanNotationEnabled: Boolean = false,
    val transpositions: Map<String, Int> = emptyMap(),
    val selectedTags: List<String> = emptyList(),
    val tagMatchMode: String = "",
)
