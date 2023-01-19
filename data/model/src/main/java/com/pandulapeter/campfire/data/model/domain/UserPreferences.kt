package com.pandulapeter.campfire.data.model.domain

data class UserPreferences(
    val shouldShowExplicitSongs: Boolean,
    val shouldShowSongsWithoutChords: Boolean,
    val unselectedDatabaseUrls: List<String>
)