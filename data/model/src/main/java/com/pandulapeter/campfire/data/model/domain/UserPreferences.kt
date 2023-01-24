package com.pandulapeter.campfire.data.model.domain

data class UserPreferences(
    val shouldShowExplicitSongs: Boolean,
    val shouldShowSongsWithoutChords: Boolean,
    val unselectedDatabaseUrls: List<String>,
    val uiMode: UiMode,
    val language: Language
) {

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