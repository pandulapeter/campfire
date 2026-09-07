package com.pandulapeter.campfire.presentation

import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::CampfireViewModel)
}
