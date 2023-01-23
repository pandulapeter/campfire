package com.pandulapeter.campfire.shared

import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import org.koin.dsl.module

val presentationModule = module {
    single { CampfireViewModel(get(), get(), get(), get(), get()) }
}