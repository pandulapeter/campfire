package com.pandulapeter.campfire.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.catalogue.CampfireDesktopTheme
import com.pandulapeter.campfire.presentation.screens.PlaylistsScreensDesktop
import com.pandulapeter.campfire.presentation.screens.SettingsScreensDesktop
import com.pandulapeter.campfire.presentation.screens.SongsScreenDesktop
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireBottomNavigationBar
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireNavigationRail
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireScaffold
import org.koin.java.KoinJavaComponent

@Composable
fun CampfireDesktopApp(
    viewModel: CampfireViewModel = KoinJavaComponent.get(CampfireViewModel::class.java),
    windowSize: DpSize
) {
    val uiMode = viewModel.uiMode.collectAsState(null)
    val userPreferences = viewModel.userPreferences.collectAsState(null)

    LaunchedEffect(Unit) { viewModel.onInitialize() }

    CampfireDesktopTheme(
        uiMode = uiMode.value
    ) {
        val selectedNavigationDestination = viewModel.selectedNavigationDestination.collectAsState(initial = null)
        val navigationDestinations = viewModel.navigationDestinations.collectAsState(initial = emptyList())

        CampfireScaffold(
            navigationDestinations = navigationDestinations.value,
            isInLandscape = windowSize.width > windowSize.height,
            userPreferences = userPreferences.value,
            bottomNavigationBar = {
                CampfireBottomNavigationBar(
                    navigationDestinations = navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                    userPreferences = userPreferences.value
                )
            },
            navigationRail = { scaffoldPadding, content ->
                NavigationRailWrapper(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                    navigationDestinations = navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                    userPreferences = userPreferences.value,
                    content = content
                )
            },
            content = { scaffoldPadding ->
                Content(
                    modifier = scaffoldPadding?.let {
                        Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding)
                    } ?: Modifier,
                    selectedNavigationDestination = selectedNavigationDestination.value,
                    shouldUseExpandedUi = scaffoldPadding == null // TODO: Should be based on screen width
                )
            }
        )
    }
}

@Composable
private fun Content(
    modifier: Modifier = Modifier,
    selectedNavigationDestination: CampfireViewModel.NavigationDestination?,
    shouldUseExpandedUi: Boolean
) = Crossfade(
    modifier = modifier.fillMaxSize(),
    targetState = selectedNavigationDestination
) { destination ->
    when (destination) {
        CampfireViewModel.NavigationDestination.SONGS -> SongsScreenDesktop(shouldUseExpandedUi = shouldUseExpandedUi)
        CampfireViewModel.NavigationDestination.PLAYLISTS -> PlaylistsScreensDesktop()
        CampfireViewModel.NavigationDestination.SETTINGS -> SettingsScreensDesktop()
        null -> Unit
    }
}

@Composable
private fun NavigationRailWrapper(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    userPreferences: UserPreferences?,
    content: @Composable () -> Unit
) = Row(
    modifier = modifier
) {
    CampfireNavigationRail(
        navigationDestinations = navigationDestinations,
        onNavigationDestinationSelected = onNavigationDestinationSelected,
        userPreferences = userPreferences
    )
    content()
}