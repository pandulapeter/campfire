package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.RawSongDetailsRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.TranspositionRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.repository.implementation.SetlistRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.RawSongDetailsRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.SongRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.TranspositionRepositoryImpl
import com.pandulapeter.campfire.data.repository.implementation.UserPreferencesRepositoryImpl
import org.koin.dsl.module

val dataRepositoryModule = module {
    single<SetlistRepository> { SetlistRepositoryImpl(get()) }
    single<SongRepository> { SongRepositoryImpl(get()) }
    single<RawSongDetailsRepository> { RawSongDetailsRepositoryImpl(get()) }
    single<UserPreferencesRepository> { UserPreferencesRepositoryImpl(get()) }
    single<TranspositionRepository> { TranspositionRepositoryImpl(get()) }
}