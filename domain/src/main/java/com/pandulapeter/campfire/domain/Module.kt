package com.pandulapeter.campfire.domain

import com.pandulapeter.campfire.domain.useCases.GetCollectionsUseCase
import com.pandulapeter.campfire.domain.useCases.GetSongsUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { GetCollectionsUseCase(get(), get()) }
    factory { GetSongsUseCase(get(), get()) }
}