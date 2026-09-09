package com.pandulapeter.campfire

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.pandulapeter.campfire.data.repository.dataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementation.dataLocalSourceModule
import com.pandulapeter.campfire.domain.implementation.domainModule
import com.pandulapeter.campfire.presentation.presentationModule
import com.pandulapeter.campfire.presentation.ui.CampfireDesktopApp
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.handleKeyEvent
import com.pandulapeter.campfire.resources.Res
import com.pandulapeter.campfire.resources.app_icon
import java.awt.Dimension
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinConfiguration

private val dataModules
    get() = dataLocalSourceModule + dataRepositoryModule

fun main() = application {
    // The view model is created inside the window (which owns the ViewModelStore), but the key handler needs it here.
    val viewModel = remember { mutableStateOf<CampfireViewModel?>(null) }
    Window(
        title = "Campfire",
        onCloseRequest = ::exitApplication,
        icon = painterResource(Res.drawable.app_icon),
        onKeyEvent = { keyEvent -> viewModel.value?.handleKeyEvent(keyEvent, onExit = ::exitApplication) == true }
    ) {
        window.minimumSize = Dimension(400, 400)
        KoinApplication(
            koinConfiguration { modules(dataModules + domainModule + presentationModule) }
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
