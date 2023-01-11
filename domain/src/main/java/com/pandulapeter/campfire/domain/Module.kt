package com.pandulapeter.campfire.domain

import com.pandulapeter.campfire.domain.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.useCases.SaveDatabasesUseCase
import com.pandulapeter.campfire.domain.useCases.SavePlaylistsUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { GetScreenDataUseCase(get(), get(), get(), get()) }
    factory { LoadScreenDataUseCase(get(), get(), get(), get()) }
    factory { SaveDatabasesUseCase(get()) }
    factory { SavePlaylistsUseCase(get()) }
}