package com.pandulapeter.campfire

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pandulapeter.campfire.data.repository.dataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementationDesktop.dataLocalSourceDesktopModule
import com.pandulapeter.campfire.data.source.remote.implementationJvm.dataRemoteSourceJvmModule
import com.pandulapeter.campfire.domain.implementation.domainModule
import com.pandulapeter.campfire.presentation.CampfireDesktopApp
import com.pandulapeter.campfire.shared.presentationModule
import org.koin.core.context.startKoin
import java.awt.Dimension

private val dataModules
    get() = dataLocalSourceDesktopModule + dataRemoteSourceJvmModule + dataRepositoryModule

fun main() = application {
    startKoin { modules(dataModules + domainModule + presentationModule) }

    val state = rememberWindowState()

    Window(
        title = "Campfire",
        onCloseRequest = ::exitApplication,
        state = state
    ) {
        window.minimumSize = Dimension(400, 400)
        CampfireDesktopApp(
            windowSize = state.size
        )
    }
}