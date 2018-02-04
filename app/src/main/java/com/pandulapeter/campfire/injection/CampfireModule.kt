package com.pandulapeter.campfire.injection

import com.google.gson.GsonBuilder
import com.pandulapeter.campfire.data.repository.*
import com.pandulapeter.campfire.data.storage.DataStorageManager
import com.pandulapeter.campfire.data.storage.FileStorageManager
import com.pandulapeter.campfire.data.storage.PreferenceStorageManager
import com.pandulapeter.campfire.feature.detail.DetailEventBus
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.integration.DeepLinkManager
import com.pandulapeter.campfire.networking.AnalyticsManager
import com.pandulapeter.campfire.networking.NetworkManager
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

val bridgeModule: Module = applicationContext {
    provide { DetailEventBus() }
}