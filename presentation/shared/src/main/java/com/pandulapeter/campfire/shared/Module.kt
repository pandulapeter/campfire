package com.pandulapeter.campfire.shared

import com.pandulapeter.campfire.shared.ui.TestUiStateHolder
import org.koin.dsl.module

val presentationModule = module {
    single { TestUiStateHolder(get(), get(), get(), get(), get()) }
}