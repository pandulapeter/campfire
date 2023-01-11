package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.api.PlaylistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.repository.implementation.CollectionRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.DatabaseRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.PlaylistRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.SongRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.UserPreferencesRepositoryImpl
import org.koin.dsl.module

val dataRepositoryModule = module {
    single<CollectionRepository> { CollectionRepositoryImpl(get(), get()) }
    single<DatabaseRepository> { DatabaseRepositoryImpl(get()) }
    single<PlaylistRepository> { PlaylistRepositoryImpl(get()) }
    single<SongRepository> { SongRepositoryImpl(get(), get()) }
    single<UserPreferencesRepository> { UserPreferencesRepositoryImpl(get()) }
}