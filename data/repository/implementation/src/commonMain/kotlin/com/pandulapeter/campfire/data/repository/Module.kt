package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.repository.implementation.ArchiveRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.SetlistRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.SongContentRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.SongRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.SyncRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.UserPreferencesRepositoryImpl
import org.koin.dsl.module

val dataRepositoryModule = module {
    single<ArchiveRepository> { ArchiveRepositoryImpl(get()) }
    single<SetlistRepository> { SetlistRepositoryImpl(get()) }
    single<SongContentRepository> { SongContentRepositoryImpl(get()) }
    single<SongRepository> { SongRepositoryImpl(get(), get()) }
    single<SyncRepository> { SyncRepositoryImpl(get(), get(), get(), get(), get(), get(), get()) }
    single<UserPreferencesRepository> { UserPreferencesRepositoryImpl(get()) }
}
