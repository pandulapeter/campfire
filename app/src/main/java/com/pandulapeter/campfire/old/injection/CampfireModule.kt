package com.pandulapeter.campfire.old.injection

import com.google.gson.GsonBuilder
import com.pandulapeter.campfire.old.data.repository.*
import com.pandulapeter.campfire.old.data.storage.DataStorageManager
import com.pandulapeter.campfire.old.data.storage.FileStorageManager
import com.pandulapeter.campfire.old.data.storage.PreferenceStorageManager
import com.pandulapeter.campfire.old.feature.detail.DetailEventBus
import com.pandulapeter.campfire.old.feature.detail.songPage.SongParser
import com.pandulapeter.campfire.old.integration.AppShortcutManager
import com.pandulapeter.campfire.old.integration.DeepLinkManager
import com.pandulapeter.campfire.old.networking.AnalyticsManager
import com.pandulapeter.campfire.old.networking.NetworkManager
import org.koin.dsl.module.Module
import org.koin.dsl.module.applicationContext

/**
 * Defines all modules used for dependency injections.
 */

val integrationModule: Module = applicationContext {
    provide { AppShortcutManager(get(), get(), get()) }
    provide { DeepLinkManager() }
}

val networkingModule: Module = applicationContext {
    provide { GsonBuilder().create() }
    provide { AnalyticsManager() }
    provide { NetworkManager(get()) }
}

val repositoryModule: Module = applicationContext {
    provide { DownloadedSongRepository(get(), get(), get()) }
    provide { FirstTimeUserExperienceRepository(get()) }
    provide { HistoryRepository(get()) }
    provide { LanguageRepository(get()) }
    provide { PlaylistRepository(get()) }
    provide { SongInfoRepository(get(), get(), get(), get()) }
    provide { UserPreferenceRepository(get()) }
}

val storageModule: Module = applicationContext {
    provide { PreferenceStorageManager(get()) }
    provide { DataStorageManager(get(), get()) }
    provide { FileStorageManager(get()) }
}

val detailModule: Module = applicationContext {
    provide { DetailEventBus() }
    provide { SongParser(get()) }
}