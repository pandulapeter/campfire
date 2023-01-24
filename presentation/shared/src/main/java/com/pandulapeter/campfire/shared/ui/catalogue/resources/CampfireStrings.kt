package com.pandulapeter.campfire.shared.ui.catalogue.resources

sealed class CampfireStrings {

    // General
    abstract val songs: String
    abstract val playlists: String
    abstract val settings: String

    // Songs screen
    abstract val songsAllDatabases: String
    abstract val songsFilters: String
    abstract val songsSearch: String
    abstract val songsClear: String
    abstract val songsShowExplicit: String
    abstract val songsShowWithoutChords: String
    abstract val songsDatabaseFilter: (databaseName: String) -> String
    abstract val songsActions: String
    abstract val songsRefresh: String
    abstract val songsDeleteLocalData: String
    abstract val songsNoData: String
    abstract val songsHeader: (songCount: Int) -> String

    object English : CampfireStrings() {
        override val songs = "Songs"
        override val playlists = "Playlists"
        override val settings = "Settings"
        override val songsAllDatabases = "All databases"
        override val songsFilters = "Filters"
        override val songsSearch = "Search"
        override val songsClear = "Clear"
        override val songsShowExplicit = "Show explicit songs"
        override val songsShowWithoutChords = "Show songs without chords"
        override val songsDatabaseFilter: (databaseName: String) -> String = { "Database $it" }
        override val songsActions = "Actions"
        override val songsRefresh = "Refresh"
        override val songsDeleteLocalData = "Delete saved songs"
        override val songsNoData = "No songs to show"
        override val songsHeader: (songCount: Int) -> String = { "Songs ($it)" }
    }

    object Hungarian : CampfireStrings() {
        override val songs = "Dalok"
        override val playlists = "Listák"
        override val settings = "Beállítások"
        override val songsAllDatabases = "Minden adatbázis"
        override val songsFilters = "Szűrők"
        override val songsSearch = "Keresés"
        override val songsClear = "Törlés"
        override val songsShowExplicit = "Explicit dalok mutatása"
        override val songsShowWithoutChords = "Akkord nélküli dalok mutatása"
        override val songsDatabaseFilter: (databaseName: String) -> String = { "$it adatbázis" }
        override val songsActions = "Akciók"
        override val songsRefresh = "Frissítés"
        override val songsDeleteLocalData = "Mentett dalok törlése"
        override val songsNoData = "Nincsenek dalok"
        override val songsHeader: (songCount: Int) -> String = { "Dalok ($it)" }
    }
}