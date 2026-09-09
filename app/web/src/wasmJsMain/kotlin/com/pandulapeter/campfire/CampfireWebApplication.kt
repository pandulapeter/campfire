package com.pandulapeter.campfire

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.pandulapeter.campfire.data.repository.dataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementation.dataLocalSourceModule
import com.pandulapeter.campfire.domain.implementation.domainModule
import com.pandulapeter.campfire.presentation.presentationModule
import com.pandulapeter.campfire.presentation.ui.CampfireWebApp
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

private val dataModules
    get() = dataLocalSourceModule + dataRepositoryModule

@OptIn(ExperimentalComposeUiApi::class)
fun main() = ComposeViewport {
    KoinApplication(
        koinConfiguration { modules(dataModules + domainModule + presentationModule) }
    ) {
        CampfireWebApp()
    }
}
