package com.pandulapeter.campfire.shared.ui.catalogue.resources

object CampfireStrings {

    data class UiStrings(
        val songs: String,
        val playlists: String,
        val settings: String
    )

    val english = UiStrings(
        songs = "Songs",
        playlists = "Playlists",
        settings = "Settings"
    )
    val hungarian = UiStrings(
        songs = "Dalok",
        playlists = "Listák",
        settings = "Beállítások"
    )
}