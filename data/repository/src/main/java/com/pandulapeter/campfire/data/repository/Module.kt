package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.repository.api.AppStartupRepository
import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.implementation.AppStartupRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.CollectionRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.SongRepositoryImpl
import org.koin.dsl.module

val dataRepositoryModule = module {
    single<AppStartupRepository> { AppStartupRepositoryImpl() }
    single<CollectionRepository> { CollectionRepositoryImpl(get(), get()) }
    single<SongRepository> { SongRepositoryImpl(get(), get()) }
}