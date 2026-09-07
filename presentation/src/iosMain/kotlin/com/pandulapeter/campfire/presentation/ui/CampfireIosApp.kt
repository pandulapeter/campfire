package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

/**
 * iOS shell of the shared UI.
 *
 * @param urlOpener Opens the given URL in Safari.
 */
@Composable
fun CampfireIosApp(
    viewModel: CampfireViewModel = koinViewModel(),
    urlOpener: (String) -> Unit
) = CampfireApp(
    viewModel = viewModel,
    urlOpener = urlOpener
)
