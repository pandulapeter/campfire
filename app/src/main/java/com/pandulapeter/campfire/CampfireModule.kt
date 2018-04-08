package com.pandulapeter.campfire

import android.arch.persistence.room.Room
import com.google.gson.GsonBuilder
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.persistence.SongDatabase
import com.pandulapeter.campfire.data.repository.ChangelogRepository
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.integration.DeepLinkManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import org.koin.dsl.module.applicationContext

val integrationModule = applicationContext {
    provide { AppShortcutManager(get()) }
    provide { DeepLinkManager() }
    provide { FirstTimeUserExperienceManager(get()) }
}

val networkingModule = applicationContext {
    provide { GsonBuilder().create() }
    provide { AnalyticsManager(get(), get()) }
    provide { NetworkManager(get()) }
}

val repositoryModule = applicationContext {
    provide { SongRepository(get(), get(), get()) }
    provide { SongDetailRepository(get(), get()) }
    provide { ChangelogRepository() }
    provide { HistoryRepository(get()) }
}

val persistenceModule = applicationContext {
    provide { PreferenceDatabase(get()) }
    provide { Room.databaseBuilder(get(), SongDatabase::class.java, "songDatabase.db").build() }
}