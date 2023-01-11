package com.pandulapeter.campfire.domain.implementation

import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveDatabasesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SavePlaylistsUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveUserPreferencesUseCase
import com.pandulapeter.campfire.domain.implementation.useCases.GetScreenDataUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.LoadScreenDataUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SaveDatabasesUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SavePlaylistsUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SaveUserPreferencesUseCaseImpl
import org.koin.dsl.module

val domainModule = module {
    factory<GetScreenDataUseCase> { GetScreenDataUseCaseImpl(get(), get(), get(), get(), get()) }
    factory<LoadScreenDataUseCase> { LoadScreenDataUseCaseImpl(get(), get(), get(), get(), get()) }
    factory<SaveDatabasesUseCase> { SaveDatabasesUseCaseImpl(get()) }
    factory<SavePlaylistsUseCase> { SavePlaylistsUseCaseImpl(get()) }
    factory<SaveUserPreferencesUseCase> { SaveUserPreferencesUseCaseImpl(get()) }
}