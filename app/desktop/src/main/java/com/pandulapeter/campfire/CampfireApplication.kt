package com.pandulapeter.campfire

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.pandulapeter.campfire.data.repository.dataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementationDesktop.dataLocalSourceDesktopModule
import com.pandulapeter.campfire.data.source.remote.implementationJvm.dataRemoteSourceJvmModule
import com.pandulapeter.campfire.domain.implementation.domainModule
import com.pandulapeter.campfire.presentation.CampfireApp
import org.koin.core.context.startKoin

private val dataModules
    get() = dataLocalSourceDesktopModule + dataRemoteSourceJvmModule + dataRepositoryModule

fun main() = application {
    startKoin { modules(dataModules + domainModule) }
    Window(
        title = "Campfire",
        onCloseRequest = ::exitApplication
    ) {
        CampfireApp()
    }
}