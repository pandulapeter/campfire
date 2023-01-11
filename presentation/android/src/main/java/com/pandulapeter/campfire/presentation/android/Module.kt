package com.pandulapeter.campfire.presentation.android

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val presentationAndroidModule = module {
    viewModel { MainViewModel(get(), get()) }
}