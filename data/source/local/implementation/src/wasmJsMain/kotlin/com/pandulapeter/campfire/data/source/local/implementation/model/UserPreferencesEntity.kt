package com.pandulapeter.campfire.data.source.local.implementation.model

import kotlinx.serialization.Serializable

@Serializable
internal data class UserPreferencesEntity(
    val shouldShowSongsWithoutChords: Boolean,
    val showOnlyDownloadedSongs: Boolean,
    val isLyricsOnlyModeEnabled: Boolean = false,
    val isHorizontalSectionFlowEnabled: Boolean = false,
    val fontScale: Float = 1f,
    val unselectedDatabaseUrls: List<String>,
    val sortingMode: String,
    val uiMode: String,
    val language: String
)
