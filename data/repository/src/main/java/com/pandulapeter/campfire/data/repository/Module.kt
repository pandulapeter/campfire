package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.repository.api.SheetRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.implementation.CollectionRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.SheetRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.SongRepositoryImpl
import org.koin.dsl.module

val dataRepositoryModule = module {
    single<CollectionRepository> { CollectionRepositoryImpl(get(), get()) }
    single<SheetRepository> { SheetRepositoryImpl(get()) }
    single<SongRepository> { SongRepositoryImpl(get(), get()) }
}