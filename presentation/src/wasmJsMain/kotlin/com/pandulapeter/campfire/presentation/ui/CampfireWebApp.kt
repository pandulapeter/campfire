package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import kotlinx.browser.window
import org.koin.compose.viewmodel.koinViewModel

/**
 * Web shell of the shared UI. Links open in a new browser tab.
 */
@Composable
fun CampfireWebApp(
    viewModel: CampfireViewModel = koinViewModel()
) = CampfireApp(
    viewModel = viewModel,
    urlOpener = { url -> window.open(url, "_blank") }
)
