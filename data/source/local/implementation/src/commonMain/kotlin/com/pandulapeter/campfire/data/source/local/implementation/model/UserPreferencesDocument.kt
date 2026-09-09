package com.pandulapeter.campfire.data.source.local.implementation.model

import kotlinx.serialization.Serializable

/**
 * The on-disk shape of `preferences.json`. Every field is defaulted, so a document written by an older version keeps
 * loading instead of being discarded; the enums are stored by their stable `id` rather than by ordinal.
 */
@Serializable
internal data class UserPreferencesDocument(
    // On by default: a song created in the app starts out as a title and an artist, so hiding songs without chords
    // would hide every new song right after it was made.
    val shouldShowSongsWithoutChords: Boolean = true,
    val isLyricsOnlyModeEnabled: Boolean = false,
    val isHorizontalSectionFlowEnabled: Boolean = false,
    val fontScale: Float = 1f,
    val sortingMode: String = "",
    val uiMode: String = "",
    val language: String = "",
    val transpositions: Map<String, Int> = emptyMap()
)
