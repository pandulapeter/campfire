package com.pandulapeter.campfire.domain

import com.pandulapeter.campfire.domain.useCases.AreCollectionsAvailableUseCase
import com.pandulapeter.campfire.domain.useCases.AreSongsAvailableUseCase
import com.pandulapeter.campfire.domain.useCases.GetCollectionByIdUseCase
import com.pandulapeter.campfire.domain.useCases.GetCollectionsUseCase
import com.pandulapeter.campfire.domain.useCases.GetSongsUseCase
import com.pandulapeter.campfire.domain.useCases.IsAppStartupUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { AreCollectionsAvailableUseCase(get()) }
    factory { AreSongsAvailableUseCase(get()) }
    factory { GetCollectionByIdUseCase(get()) }
    factory { GetCollectionsUseCase(get()) }
    factory { GetSongsUseCase(get()) }
    factory { IsAppStartupUseCase(get()) }
}