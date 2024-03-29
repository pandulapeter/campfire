package com.pandulapeter.campfire.shared.ui.catalogue.resources

sealed class CampfireStrings {

    // Songs screen
    abstract val songs: String
    abstract val songsClose: String
    abstract val songsUnsortedLabel: String
    abstract val songsFilters: String
    abstract val songsSearch: String
    abstract val songsClear: String
    abstract val songsShowExplicit: String
    abstract val songsShowWithoutChords: String
    abstract val showOnlyDownloadedSongs: String
    abstract val songsDatabaseFilter: (databaseName: String) -> String
    abstract val songsSortingMode: String
    abstract val songsSortingModeByArtist: String
    abstract val songsSortingModeByTitle: String
    abstract val songsNoData: String
    abstract val songsLyricsOnly: String

    // Setlists screen
    abstract val setlists: String
    abstract val setlistsNoData: String
    abstract val setlistsNewSetlist: String
    abstract val setlistsNewSetlistTitle: String
    abstract val setlistsCreate: String
    abstract val setlistsRemoveSong: String

    // Settings screen
    abstract val settings: String
    abstract val settingsActiveDatabases: String
    abstract val settingsAddNewDatabase: String
    abstract val settingsAddNewDatabaseName: String
    abstract val settingsAddNewDatabaseUrl: String
    abstract val settingsAddNewDatabaseHint: String
    abstract val settingsAdd: String
    abstract val settingsUserInterfaceTheme: String
    abstract val settingsUserInterfaceThemeSystemDefault: String
    abstract val settingsUserInterfaceThemeDark: String
    abstract val settingsUserInterfaceThemeLight: String
    abstract val settingsUserInterfaceLanguage: String
    abstract val settingsUserInterfaceLanguageSystemDefault: String
    abstract val settingsUserInterfaceLanguageEnglish: String
    abstract val settingsUserInterfaceLanguageHungarian: String
    abstract val settingsAbout: String
    abstract val settingsWebsite: String
    abstract val settingsGitHub: String
    abstract val settingsPrivacyPolicy: String

    // Song details screen
    abstract val songDetailsAddToSetlist: String

    object English : CampfireStrings() {

        // Songs screen
        override val songs = "Songs"
        override val songsClose = "Close"
        override val songsUnsortedLabel = "0 - 9"
        override val songsFilters = "Filters"
        override val songsSearch = "Search in songs"
        override val songsClear = "Clear"
        override val songsShowExplicit = "Show explicit songs"
        override val songsShowWithoutChords = "Show songs without chords"
        override val showOnlyDownloadedSongs = "Show only downloaded songs"
        override val songsDatabaseFilter: (databaseName: String) -> String = { "Database $it" }
        override val songsSortingMode = "Sort by"
        override val songsSortingModeByArtist = "Artist"
        override val songsSortingModeByTitle = "Title"
        override val songsNoData = "No songs to show"
        override val songsLyricsOnly = "Lyrics only"

        // Setlists screen
        override val setlists = "Setlists"
        override val setlistsNoData = "No setlists to show"
        override val setlistsNewSetlist = "New setlist"
        override val setlistsNewSetlistTitle = "Setlist title"
        override val setlistsCreate = "Create"
        override val setlistsRemoveSong = "Remove song"

        // Settings screen
        override val settings = "Settings"
        override val settingsActiveDatabases = "Active databases"
        override val settingsAddNewDatabase = "Add a new database"
        override val settingsAddNewDatabaseName = "Database name"
        override val settingsAddNewDatabaseUrl = "Database URL"
        override val settingsAddNewDatabaseHint = "More information"
        override val settingsAdd = "Add"
        override val settingsUserInterfaceTheme = "User interface theme"
        override val settingsUserInterfaceThemeSystemDefault = "System default"
        override val settingsUserInterfaceThemeDark = "Dark"
        override val settingsUserInterfaceThemeLight = "Light"
        override val settingsUserInterfaceLanguage = "User interface language"
        override val settingsUserInterfaceLanguageSystemDefault = "System default"
        override val settingsUserInterfaceLanguageEnglish = "English"
        override val settingsUserInterfaceLanguageHungarian = "Hungarian"
        override val settingsAbout = "About"
        override val settingsWebsite = "Website"
        override val settingsGitHub = "GitHub page"
        override val settingsPrivacyPolicy = "Privacy Policy"

        // Song details screen
        override val songDetailsAddToSetlist = "Add to setlist"
    }

    object Hungarian : CampfireStrings() {

        // Songs screen
        override val songs = "Dalok"
        override val songsClose = "Bezárás"
        override val songsUnsortedLabel = "0 - 9"
        override val songsFilters = "Szűrők"
        override val songsSearch = "Keresés a dalokban"
        override val songsClear = "Törlés"
        override val songsShowExplicit = "Explicit dalok mutatása"
        override val songsShowWithoutChords = "Akkord nélküli dalok mutatása"
        override val showOnlyDownloadedSongs = "Csak letöltött dalok mutatása"
        override val songsDatabaseFilter: (databaseName: String) -> String = { "\"$it\" adatbázis" }
        override val songsSortingMode = "Rendezési mód"
        override val songsSortingModeByArtist = "Előadó"
        override val songsSortingModeByTitle = "Cím"
        override val songsNoData = "Nincsenek dalok"
        override val songsLyricsOnly = "Csak dalszöveg"

        // Setlists screen
        override val setlists = "Listák"
        override val setlistsNoData = "Nincsenek listák"
        override val setlistsNewSetlist = "Új lista"
        override val setlistsNewSetlistTitle = "Lista címe"
        override val setlistsCreate = "Létrehozás"
        override val setlistsRemoveSong = "Dal eltávolítása"

        // Settings screen
        override val settings = "Beállítások"
        override val settingsActiveDatabases = "Aktív adatbázisok"
        override val settingsAddNewDatabase = "Új adatbázis hozzáadása"
        override val settingsAddNewDatabaseName = "Adatbázis neve"
        override val settingsAddNewDatabaseUrl = "Adatbázis URL"
        override val settingsAddNewDatabaseHint = "Több információ"
        override val settingsAdd = "Hozzáadás"
        override val settingsUserInterfaceTheme = "Felhasználói felület témája"
        override val settingsUserInterfaceThemeSystemDefault = "Alapértelmezett"
        override val settingsUserInterfaceThemeDark = "Sötét"
        override val settingsUserInterfaceThemeLight = "Világos"
        override val settingsUserInterfaceLanguage = "Felhasználói felület nyelve"
        override val settingsUserInterfaceLanguageSystemDefault = "Alapértelmezett"
        override val settingsUserInterfaceLanguageEnglish = "Angol"
        override val settingsUserInterfaceLanguageHungarian = "Magyar"
        override val settingsAbout = "Az alkalmazásról"
        override val settingsWebsite = "Weboldal"
        override val settingsGitHub = "GitHub oldal"
        override val settingsPrivacyPolicy = "Adatvédelmi nyilatkozat"

        // Song details screen
        override val songDetailsAddToSetlist = "Hozzáadás listához"
    }
}