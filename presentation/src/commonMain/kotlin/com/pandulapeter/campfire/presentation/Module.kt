package com.pandulapeter.campfire.presentation

import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val presentationModule = module {
    // Spelled out rather than built with viewModelOf, whose reified overloads stop at twenty-two parameters. Named
    // arguments, so that the list stays readable and reordering the constructor cannot silently swap two use cases.
    viewModel {
        CampfireViewModel(
            getScreenData = get(),
            getUserPreferences = get(),
            getSyncState = get(),
            getSyncProviders = get(),
            loadScreenData = get(),
            getSongContent = get(),
            createSong = get(),
            deleteSong = get(),
            importFiles = get(),
            exportSongs = get(),
            exportSetlist = get(),
            exportLibrary = get(),
            createSetlist = get(),
            saveSetlist = get(),
            deleteSetlist = get(),
            saveSongContent = get(),
            saveUserPreferences = get(),
            connectSyncProvider = get(),
            disconnectSyncProvider = get(),
            cancelSynchronization = get(),
            restoreSync = get(),
            synchronizeLibrary = get(),
            normalizeText = get(),
            parseChordPro = get(),
            transposeChordPro = get(),
            transposeChordProText = get()
        )
    }
}
