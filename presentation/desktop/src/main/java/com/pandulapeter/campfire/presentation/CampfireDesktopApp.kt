package com.pandulapeter.campfire.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.pandulapeter.campfire.shared.ui.CampfireApp
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import org.koin.compose.viewmodel.koinViewModel
import java.awt.Desktop
import java.net.URI

/**
 * Desktop shell of the shared UI. Desktop has no back gesture, so the Escape key (and the mouse back button, see
 * [handleKeyEvent]) pops the back stack.
 */
@Composable
fun CampfireDesktopApp(
    viewModel: CampfireViewModel = koinViewModel()
) = CampfireApp(
    viewModel = viewModel,
    urlOpener = ::openUrl
)

/**
 * To be wired into the window's key event handler. Returns true if the event was consumed.
 */
fun CampfireViewModel.handleKeyEvent(keyEvent: KeyEvent): Boolean {
    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape && backStack.size > 1) {
        navigateBack()
        return true
    }
    return false
}

private fun openUrl(url: String) {
    try {
        val desktop = Desktop.getDesktop()
        val osName by lazy(LazyThreadSafetyMode.NONE) { System.getProperty("os.name").lowercase() }
        when {
            Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE) -> desktop.browse(URI(url))
            "mac" in osName -> Runtime.getRuntime().exec(arrayOf("open", url))
            "nix" in osName || "nux" in osName -> Runtime.getRuntime().exec(arrayOf("xdg-open", url))
            else -> println("Cannot open url: $url")
        }
    } catch (_: NoClassDefFoundError) {
        println("Cannot open url: $url")
    }
}
