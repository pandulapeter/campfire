package com.pandulapeter.campfire.shared

import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::CampfireViewModel)
}
