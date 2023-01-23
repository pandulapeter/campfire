package com.pandulapeter.campfire.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.catalogue.CampfireDesktopTheme
import com.pandulapeter.campfire.presentation.screens.CollectionsScreensDesktop
import com.pandulapeter.campfire.presentation.screens.HomeScreenDesktop
import com.pandulapeter.campfire.presentation.screens.PlaylistsScreensDesktop
import com.pandulapeter.campfire.presentation.screens.SettingsScreensDesktop
import com.pandulapeter.campfire.presentation.screens.SongsScreensDesktop
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import org.koin.java.KoinJavaComponent

@Composable
fun CampfireDesktopApp(
    viewModel: CampfireViewModel = KoinJavaComponent.get(CampfireViewModel::class.java)
) {
    val uiMode = viewModel.uiMode.collectAsState(null)

    CampfireDesktopTheme(
        uiMode = uiMode.value
    ) {
        val selectedNavigationDestination = viewModel.selectedNavigationDestination.collectAsState(initial = null)
        val navigationDestinations = viewModel.navigationDestinations.collectAsState(initial = emptyList())

        Row(
            modifier = Modifier.background(MaterialTheme.colors.background)
        ) {
            CampfireNavigationRail(
                navigationDestinations = navigationDestinations.value,
                onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected
            )
            Crossfade(
                modifier = Modifier.fillMaxSize(),
                targetState = selectedNavigationDestination.value
            ) { selectedNavigationDestination ->
                when (selectedNavigationDestination) {
                    CampfireViewModel.NavigationDestination.HOME -> HomeScreenDesktop()
                    CampfireViewModel.NavigationDestination.COLLECTIONS -> CollectionsScreensDesktop()
                    CampfireViewModel.NavigationDestination.SONGS -> SongsScreensDesktop()
                    CampfireViewModel.NavigationDestination.PLAYLISTS -> PlaylistsScreensDesktop()
                    CampfireViewModel.NavigationDestination.SETTINGS -> SettingsScreensDesktop()
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun CampfireNavigationRail(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit
) = NavigationRail(
    modifier = modifier
) {
    navigationDestinations.forEach { navigationDestination ->
        NavigationRailItem(
            selected = navigationDestination.isSelected,
            onClick = { onNavigationDestinationSelected(navigationDestination.destination) },
            label = {
                Text(navigationDestination.destination.displayName)
            },
            icon = {
                Icon(
                    imageVector = navigationDestination.destination.icon,
                    contentDescription = navigationDestination.destination.displayName,
                )
            }
        )
    }
}