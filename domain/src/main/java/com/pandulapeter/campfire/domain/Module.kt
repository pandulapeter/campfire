package com.pandulapeter.campfire.domain

import com.pandulapeter.campfire.domain.useCases.AreCollectionsAvailableUseCase
import com.pandulapeter.campfire.domain.useCases.GetCollectionByIdUseCase
import com.pandulapeter.campfire.domain.useCases.GetCollectionsUseCase
import com.pandulapeter.campfire.domain.useCases.IsAppStartupUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { AreCollectionsAvailableUseCase(get()) }
    factory { GetCollectionByIdUseCase(get()) }
    factory { GetCollectionsUseCase(get()) }
    factory { IsAppStartupUseCase(get()) }
}