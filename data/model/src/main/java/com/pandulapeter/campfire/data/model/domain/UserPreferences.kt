package com.pandulapeter.campfire.data.model.domain

data class UserPreferences(
    val shouldShowExplicitSongs: Boolean,
    val shouldShowSongsWithoutChords: Boolean,
    val unselectedDatabaseUrls: List<String>,
    val uiMode: UiMode
) {

    enum class UiMode(val id: String) {
        LIGHT("light"),
        DARK("dark"),
        SYSTEM_DEFAULT("system_default")
    }
}