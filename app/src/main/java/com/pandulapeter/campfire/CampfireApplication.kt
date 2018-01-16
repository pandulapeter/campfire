package com.pandulapeter.campfire

import android.app.Application
import com.google.gson.GsonBuilder
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.LanguageRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.storage.DataStorageManager
import com.pandulapeter.campfire.data.storage.FileStorageManager
import com.pandulapeter.campfire.data.storage.PreferenceStorageManager
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.integration.DeepLinkManager
import com.pandulapeter.campfire.networking.AnalyticsManager
import com.pandulapeter.campfire.networking.NetworkManager
import org.koin.android.ext.android.startKoin
import org.koin.dsl.module.Module
import org.koin.dsl.module.applicationContext

/**
 * Custom Application class for handling dependency injection.
 *
 * TODO: Introduce Firebase crash reporting.
 */
class CampfireApplication : Application() {

    private val integrationModule: Module = applicationContext {
        provide { AppShortcutManager(get(), get(), get()) }
        provide { DeepLinkManager() }
    }
    private val networkingModule: Module = applicationContext {
        provide { GsonBuilder().create() }
        provide { AnalyticsManager() }
        provide { NetworkManager(get()) }
    }
    private val repositoryModule: Module = applicationContext {
        provide { DownloadedSongRepository(get(), get(), get()) }
        provide { FirstTimeUserExperienceRepository(get()) }
        provide { HistoryRepository(get()) }
        provide { LanguageRepository(get()) }
        provide { PlaylistRepository(get()) }
        provide { SongInfoRepository(get(), get(), get(), get()) }
        provide { UserPreferenceRepository(get()) }
    }
    private val storageModule: Module = applicationContext {
        provide { PreferenceStorageManager(get()) }
        provide { DataStorageManager(get(), get()) }
        provide { FileStorageManager(get()) }
    }

    override fun onCreate() {
        super.onCreate()
        startKoin(this, listOf(integrationModule, networkingModule, repositoryModule, storageModule))
    }
}