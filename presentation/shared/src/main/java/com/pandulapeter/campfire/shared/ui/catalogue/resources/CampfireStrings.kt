package com.pandulapeter.campfire.shared.ui.catalogue.resources

sealed class CampfireStrings {

    // Songs screen
    abstract val songs: String
    abstract val songsFilters: (songCount: Int) -> String
    abstract val songsSearch: String
    abstract val songsClear: String
    abstract val songsShowExplicit: String
    abstract val songsShowWithoutChords: String
    abstract val songsDatabaseFilter: (databaseName: String) -> String
    abstract val songsSortingMode: String
    abstract val songsSortingModeByArtist: String
    abstract val songsSortingModeByTitle: String
    abstract val songsActions: String
    abstract val songsRefresh: String
    abstract val songsDeleteLocalData: String
    abstract val songsNoData: String

    // Setlists screen
    abstract val setlists: String

    // Settings screen
    abstract val settings: String
    abstract val settingsActiveDatabases: String
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
        override val songsFilters: (songCount: Int) -> String = { "Filters ($it songs)" }
        override val songsSearch = "Search"
        override val songsClear = "Clear"
        override val songsShowExplicit = "Show explicit songs"
        override val songsShowWithoutChords = "Show songs without chords"
        override val songsDatabaseFilter: (databaseName: String) -> String = { "Database $it" }
        override val songsSortingMode = "Sort by"
        override val songsSortingModeByArtist = "Artist"
        override val songsSortingModeByTitle = "Title"
        override val songsActions = "Actions"
        override val songsRefresh = "Refresh"
        override val songsDeleteLocalData = "Delete saved songs"
        override val songsNoData = "No songs to show"

        // Setlists screen
        override val setlists = "Setlists"

        // Settings screen
        override val settings = "Settings"
        override val settingsActiveDatabases = "Active databases"
        override val settingsAddNewDatabase = "Add a new database"
        override val settingsUserInterfaceTheme = "User interface theme"
        override val settingsUserInterfaceThemeSystemDefault = "System default"
        override val settingsUserInterfaceThemeDark = "Dark"
        override val settingsUserInterfaceThemeLight = "Light"
        override val settingsUserInterfaceLanguage = "User interface language"
        override val settingsUserInterfaceLanguageSystemDefault = "System default"
        override val settingsUserInterfaceLanguageEnglish = "English"
        override val settingsUserInterfaceLanguageHungarian = "Hungarian"
    }

    object Hungarian : CampfireStrings() {

        // Songs screen
        override val songs = "Dalok"
        override val songsFilters: (songCount: Int) -> String = { "Szűrők ($it dal)" }
        override val songsSearch = "Keresés"
        override val songsClear = "Törlés"
        override val songsShowExplicit = "Explicit dalok mutatása"
        override val songsShowWithoutChords = "Akkord nélküli dalok mutatása"
        override val songsDatabaseFilter: (databaseName: String) -> String = { "\"$it\" adatbázis" }
        override val songsSortingMode = "Rendezési mód"
        override val songsSortingModeByArtist = "Előadó"
        override val songsSortingModeByTitle = "Cím"
        override val songsActions = "Akciók"
        override val songsRefresh = "Frissítés"
        override val songsDeleteLocalData = "Mentett dalok törlése"
        override val songsNoData = "Nincsenek dalok"

        // Setlists screen
        override val setlists = "Listák"

        // Settings screen
        override val settings = "Beállítások"
        override val settingsActiveDatabases = "Aktív adatbázisok"
        override val settingsAddNewDatabase = "Új adatbázis hozzáadása"
        override val settingsUserInterfaceTheme = "Felhasználói felület témája"
        override val settingsUserInterfaceThemeSystemDefault = "Alapértelmezett"
        override val settingsUserInterfaceThemeDark = "Sötét"
        override val settingsUserInterfaceThemeLight = "Világos"
        override val settingsUserInterfaceLanguage = "Felhasználói felület nyelve"
        override val settingsUserInterfaceLanguageSystemDefault = "Alapértelmezett"
        override val settingsUserInterfaceLanguageEnglish = "Angol"
        override val settingsUserInterfaceLanguageHungarian = "Magyar"
    }
}