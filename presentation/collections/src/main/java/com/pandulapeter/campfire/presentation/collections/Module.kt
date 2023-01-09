package com.pandulapeter.campfire.presentation.collections

import com.pandulapeter.campfire.presentation.collections.implementation.CollectionsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val presentationCollectionsModule = module {
    viewModel { CollectionsViewModel(get(), get(), get()) }
}