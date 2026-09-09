package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.WebFilePicker
import kotlinx.browser.window
import org.koin.compose.viewmodel.koinViewModel

/**
 * Web shell of the shared UI. Links open in a new browser tab.
 */
@Composable
fun CampfireWebApp(
    viewModel: CampfireViewModel = koinViewModel()
) = CompositionLocalProvider(
    LocalFilePicker provides WebFilePicker
) {
    CampfireApp(
        viewModel = viewModel,
        urlOpener = { url -> window.open(url, "_blank") }
    )
}
