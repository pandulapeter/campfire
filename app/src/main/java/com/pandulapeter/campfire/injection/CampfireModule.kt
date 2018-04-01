package com.pandulapeter.campfire.injection

import android.arch.persistence.room.Room
import com.google.gson.GsonBuilder
import com.pandulapeter.campfire.data.database.PreferenceDatabase
import com.pandulapeter.campfire.data.database.SongDatabase
import com.pandulapeter.campfire.data.database.SongDetailDatabase
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.integration.DeepLinkManager
import com.pandulapeter.campfire.networking.AnalyticsManager
import com.pandulapeter.campfire.networking.NetworkManager
import org.koin.dsl.module.Module
import org.koin.dsl.module.applicationContext

val integrationModule: Module = applicationContext {
    provide { AppShortcutManager(get()) }
    provide { DeepLinkManager() }
}

val networkingModule: Module = applicationContext {
    provide { GsonBuilder().create() }
    provide { AnalyticsManager() }
    provide { NetworkManager(get()) }
}

val repositoryModule: Module = applicationContext {
    provide { SongRepository(get(), get(), get()) }
    provide { SongDetailRepository(get(), get()) }
}

val persistenceModule: Module = applicationContext {
    provide { PreferenceDatabase(get()) }
    provide { Room.databaseBuilder(get(), SongDatabase::class.java, "song.db").build() }
    provide { Room.databaseBuilder(get(), SongDetailDatabase::class.java, "songDetail.db").build() }
}