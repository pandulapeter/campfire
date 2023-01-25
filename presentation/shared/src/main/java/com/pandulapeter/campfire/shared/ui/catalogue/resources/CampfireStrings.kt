package com.pandulapeter.campfire.shared.ui.catalogue.resources

sealed class CampfireStrings {

    // Songs screen
    abstract val songs: String
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

    // Setlists screen
    abstract val setlists: String

    // Settings screen
    abstract val settings: String
    abstract val settingsAllDatabases: String
    abstract val settingsAddNewDatabase: String
    abstract val settingsUserInterfaceTheme: String
    abstract val settingsUserInterfaceThemeSystemDefault: String
    abstract val settingsUserInterfaceThemeDark: String
    abstract val settingsUserInterfaceThemeLight: String
    abstract val settingsUserInterfaceLanguage: String
    abstract val settingsUserInterfaceLanguageSystemDefault: String
    abstract val settingsUserInterfaceLanguageEnglish: String
    abstract val settingsUserInterfaceLanguageHungarian: String

    object English : CampfireStrings() {

        // Songs screen
        override val songs = "Songs"
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

        // Setlists screen
        override val setlists = "Setlists"

        // Settings screen
        override val settings = "Settings"
        override val settingsAllDatabases = "All databases"
        override val settingsAddNewDatabase = "Add new database"
        override val settingsUserInterfaceTheme = "User interface theme"
        override val settingsUserInterfaceThemeSystemDefault = "System default"
        override val settingsUserInterfaceThemeDark = "Dark"
        override val settingsUserInterfaceThemeLight = "Light"
        override val settingsUserInterfaceLanguage = "Language"
        override val settingsUserInterfaceLanguageSystemDefault = "System default"
        override val settingsUserInterfaceLanguageEnglish = "English"
        override val settingsUserInterfaceLanguageHungarian = "Hungarian"
    }

    object Hungarian : CampfireStrings() {

        // Songs screen
        override val songs = "Dalok"
        override val songsFilters = "Szűrők"
        override val songsSearch = "Keresés"
        override val songsClear = "Törlés"
        override val songsShowExplicit = "Explicit dalok mutatása"
        override val songsShowWithoutChords = "Akkord nélküli dalok mutatása"
        override val songsDatabaseFilter: (databaseName: String) -> String = { "\"$it\" adatbázis" }
        override val songsActions = "Akciók"
        override val songsRefresh = "Frissítés"
        override val songsDeleteLocalData = "Mentett dalok törlése"
        override val songsNoData = "Nincsenek dalok"
        override val songsHeader: (songCount: Int) -> String = { "Dalok ($it)" }

        // Setlists screen
        override val setlists = "Listák"

        // Settings screen
        override val settings = "Beállítások"
        override val settingsAllDatabases = "Minden adatbázis"
        override val settingsAddNewDatabase = "Új adatbázis hozzáadása"
        override val settingsUserInterfaceTheme = "Felhasználói felület témája"
        override val settingsUserInterfaceThemeSystemDefault = "Rendszer alapértelmezett"
        override val settingsUserInterfaceThemeDark = "Sötét"
        override val settingsUserInterfaceThemeLight = "Világos"
        override val settingsUserInterfaceLanguage = "Language"
        override val settingsUserInterfaceLanguageSystemDefault = "Rendszer alapértelmezett"
        override val settingsUserInterfaceLanguageEnglish = "Angol"
        override val settingsUserInterfaceLanguageHungarian = "Magyar"
    }
}