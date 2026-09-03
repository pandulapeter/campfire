package com.pandulapeter.campfire

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pandulapeter.campfire.data.repository.dataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementation.dataLocalSourceModule
import com.pandulapeter.campfire.data.source.remote.implementation.dataRemoteSourceModule
import com.pandulapeter.campfire.domain.implementation.domainModule
import com.pandulapeter.campfire.presentation.CampfireDesktopApp
import com.pandulapeter.campfire.presentation.handleKeyEvent
import com.pandulapeter.campfire.shared.presentationModule
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import java.awt.Dimension

private val dataModules
    get() = dataLocalSourceModule + dataRemoteSourceModule + dataRepositoryModule

fun main() = application {
    val windowState = rememberWindowState()
    // The view model is created inside the window (which owns the ViewModelStore), but the key handler needs it here.
    val viewModel = remember { mutableStateOf<CampfireViewModel?>(null) }
    Window(
        title = "Campfire",
        onCloseRequest = ::exitApplication,
        state = windowState,
        icon = painterResource("appIcon.png"),
        onKeyEvent = { keyEvent -> viewModel.value?.handleKeyEvent(keyEvent) == true }
    ) {
        window.minimumSize = Dimension(400, 400)
        KoinApplication(
            application = { modules(dataModules + domainModule + presentationModule) }
        ) {
            CompositionLocalProvider(
                LocalLayoutDirection.providesDefault(LayoutDirection.Ltr)
            ) {
                val currentViewModel = koinViewModel<CampfireViewModel>()
                SideEffect { viewModel.value = currentViewModel }
                CampfireDesktopApp(viewModel = currentViewModel)
            }
        }
    }
}
