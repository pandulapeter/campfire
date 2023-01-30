package com.pandulapeter.campfire.domain.implementation

import com.pandulapeter.campfire.domain.api.useCases.DeleteLocalDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongDetailsUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadSongDetailsUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveDatabasesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSetlistsUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveUserPreferencesUseCase
import com.pandulapeter.campfire.domain.implementation.useCases.DeleteLocalDataUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.GetScreenDataUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.GetSongDetailsUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.LoadScreenDataUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.LoadSongDetailsUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.NormalizeTextUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SaveDatabasesUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SaveSetlistsUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SaveUserPreferencesUseCaseImpl
import org.koin.dsl.module

val domainModule = module {
    factory<DeleteLocalDataUseCase> { DeleteLocalDataUseCaseImpl(get()) }
    factory<GetScreenDataUseCase> { GetScreenDataUseCaseImpl(get(), get(), get(), get(), get()) }
    factory<GetSongDetailsUseCase> { GetSongDetailsUseCaseImpl(get()) }
    factory<LoadScreenDataUseCase> { LoadScreenDataUseCaseImpl(get(), get(), get(), get()) }
    factory<LoadSongDetailsUseCase> { LoadSongDetailsUseCaseImpl(get()) }
    factory<NormalizeTextUseCase> { NormalizeTextUseCaseImpl() }
    factory<SaveDatabasesUseCase> { SaveDatabasesUseCaseImpl(get(), get()) }
    factory<SaveSetlistsUseCase> { SaveSetlistsUseCaseImpl(get()) }
    factory<SaveUserPreferencesUseCase> { SaveUserPreferencesUseCaseImpl(get(), get()) }
}