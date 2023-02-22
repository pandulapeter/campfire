package com.pandulapeter.campfire

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pandulapeter.campfire.data.repository.dataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementationDesktop.dataLocalSourceDesktopModule
import com.pandulapeter.campfire.data.source.remote.implementationJvm.dataRemoteSourceJvmModule
import com.pandulapeter.campfire.domain.implementation.domainModule
import com.pandulapeter.campfire.presentation.CampfireDesktopApp
import com.pandulapeter.campfire.shared.presentationModule
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent
import java.awt.Dimension

private val dataModules
    get() = dataLocalSourceDesktopModule + dataRemoteSourceJvmModule + dataRepositoryModule

fun main() = application {
    startKoin { modules(dataModules + domainModule + presentationModule) }

    val windowState = rememberWindowState()
    CompositionLocalProvider(
        LocalLayoutDirection.providesDefault(LayoutDirection.Ltr)
    ) {
        val stateHolder = CampfireViewModelStateHolder.fromViewModel(KoinJavaComponent.get(CampfireViewModel::class.java))
        Window(
            title = "Campfire",
            onCloseRequest = ::exitApplication,
            state = windowState,
            icon = painterResource("appIcon.png")
        ) {
            window.minimumSize = Dimension(400, 400)
            CampfireDesktopApp(
                stateHolder = stateHolder,
                windowSize = windowState.size
            )
        }
    }
}