package com.pandulapeter.campfire

import android.arch.persistence.room.Room
import com.google.gson.GsonBuilder
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.Database
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.*
import com.pandulapeter.campfire.feature.detail.DetailEventBus
import com.pandulapeter.campfire.feature.detail.DetailPageEventBus
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.integration.DeepLinkManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import org.koin.dsl.module.applicationContext

val integrationModule = applicationContext {
    bean { AppShortcutManager(get(), get(), get()) }
    bean { DeepLinkManager() }
    bean { FirstTimeUserExperienceManager(get()) }
}

val networkingModule = applicationContext {
    bean { GsonBuilder().create() }
    bean { AnalyticsManager(get(), get(), get()) }
    bean { NetworkManager(get()) }
}

val repositoryModule = applicationContext {
    bean { SongRepository(get(), get(), get()) }
    bean { SongDetailRepository(get(), get()) }
    bean { ChangelogRepository() }
    bean { HistoryRepository(get()) }
    bean { PlaylistRepository(get()) }
    bean { CollectionRepository(get(), get(), get()) }
}

val persistenceModule = applicationContext {
    bean { PreferenceDatabase(get()) }
    bean { Room.databaseBuilder(get(), Database::class.java, "songDatabase.db").build() }
}

val detailModule = applicationContext {
    bean { DetailEventBus() }
    bean { DetailPageEventBus() }
}