package com.pandulapeter.campfire

import androidx.room.Room
import com.google.gson.GsonBuilder
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.Database
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.*
import com.pandulapeter.campfire.feature.detail.DetailEventBus
import com.pandulapeter.campfire.feature.detail.DetailPageEventBus
import com.pandulapeter.campfire.feature.main.options.about.AboutViewModel
import com.pandulapeter.campfire.feature.main.options.changelog.ChangelogViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.integration.DeepLinkManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import org.koin.androidx.viewmodel.ext.koin.viewModel
import org.koin.dsl.module.module

val integrationModule = module {
    factory { AppShortcutManager(get(), get(), get()) }
    factory { DeepLinkManager() }
    factory { FirstTimeUserExperienceManager(get()) }
}

val networkingModule = module {
    single { GsonBuilder().create() }
    factory { AnalyticsManager(get(), get(), get()) }
    factory { NetworkManager(get()) }
}

val repositoryModule = module {
    single { SongRepository(get(), get(), get()) }
    single { SongDetailRepository(get(), get()) }
    single { ChangelogRepository() }
    single { HistoryRepository(get()) }
    single { PlaylistRepository(get()) }
    single { CollectionRepository(get(), get(), get()) }
}

val persistenceModule = module {
    single { PreferenceDatabase(get()) }
    single { Room.databaseBuilder(get(), Database::class.java, "songDatabase.db").build() }
}

val detailModule = module {
    single { DetailEventBus() }
    single { DetailPageEventBus() }
}

val featureModule = module {
    viewModel { AboutViewModel(get()) }
    viewModel { ChangelogViewModel(get()) }
}