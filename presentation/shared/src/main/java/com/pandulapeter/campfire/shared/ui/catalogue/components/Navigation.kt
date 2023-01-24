package com.pandulapeter.campfire.shared.ui.catalogue.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import com.pandulapeter.campfire.shared.ui.catalogue.theme.CampfireColors
import com.pandulapeter.campfire.shared.ui.catalogue.utilities.getUiStrings

@Composable
fun CampfireScaffold(
    modifier: Modifier = Modifier,
    statusBarModifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    isInLandscape: Boolean,
    userPreferences: UserPreferences?,
    bottomNavigationBar: @Composable () -> Unit,
    navigationRail: @Composable (scaffoldPadding: PaddingValues, content: @Composable () -> Unit) -> Unit,
    content: @Composable (scaffoldPadding: PaddingValues?) -> Unit
) = Scaffold(
    modifier = modifier,
    topBar = {
        CampfireAppBar(
            statusBarModifier = statusBarModifier,
            selectedNavigationDestination = navigationDestinations.firstOrNull { it.isSelected }?.destination,
            userPreferences = userPreferences
        )
    },
    bottomBar = {
        if (!isInLandscape) {
            bottomNavigationBar()
        }
    },
    content =
    { scaffoldPadding ->
        if (isInLandscape) navigationRail(scaffoldPadding) { content(null) } else content(scaffoldPadding)
    }
)

@Composable
fun CampfireAppBar(
    modifier: Modifier = Modifier,
    statusBarModifier: Modifier = Modifier,
    selectedNavigationDestination: CampfireViewModel.NavigationDestination?,
    userPreferences: UserPreferences?
) = TopAppBar(
    modifier = modifier,
    backgroundColor = MaterialTheme.colors.surface,
    title = {
        Text(
            modifier = statusBarModifier,
            text = CampfireStrings.getUiStrings(userPreferences).let {
                when (selectedNavigationDestination) {
                    CampfireViewModel.NavigationDestination.SONGS -> it.songs
                    CampfireViewModel.NavigationDestination.PLAYLISTS -> it.playlists
                    CampfireViewModel.NavigationDestination.SETTINGS -> it.settings
                    null -> ""
                }
            }
        )
    }
)

@Composable
fun CampfireNavigationRail(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    userPreferences: UserPreferences?
) = NavigationRail(
    modifier = modifier
) {
    navigationDestinations.forEach { navigationDestination ->
        NavigationRailItem(
            selected = navigationDestination.isSelected,
            onClick = { onNavigationDestinationSelected(navigationDestination.destination) },
            selectedContentColor = MaterialTheme.colors.primary,
            unselectedContentColor = CampfireColors.getUnselectedContentColor(),
            icon = {
                Icon(
                    imageVector = navigationDestination.destination.icon,
                    contentDescription = CampfireStrings.getUiStrings(userPreferences).let {
                        when (navigationDestination.destination) {
                            CampfireViewModel.NavigationDestination.SONGS -> it.songs
                            CampfireViewModel.NavigationDestination.PLAYLISTS -> it.playlists
                            CampfireViewModel.NavigationDestination.SETTINGS -> it.settings
                        }
                    }
                )
            }
        )
    }
}


@Composable
fun CampfireBottomNavigationBar(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    userPreferences: UserPreferences?
) = BottomAppBar(
    modifier = modifier,
    backgroundColor = MaterialTheme.colors.surface
) {
    navigationDestinations.forEach { navigationDestination ->
        BottomNavigationItem(
            selected = navigationDestination.isSelected,
            onClick = { onNavigationDestinationSelected(navigationDestination.destination) },
            selectedContentColor = MaterialTheme.colors.primary,
            unselectedContentColor = CampfireColors.getUnselectedContentColor(),
            icon = {
                Icon(
                    imageVector = navigationDestination.destination.icon,
                    contentDescription = CampfireStrings.getUiStrings(userPreferences).let {
                        when (navigationDestination.destination) {
                            CampfireViewModel.NavigationDestination.SONGS -> it.songs
                            CampfireViewModel.NavigationDestination.PLAYLISTS -> it.playlists
                            CampfireViewModel.NavigationDestination.SETTINGS -> it.settings
                        }
                    }
                )
            }
        )
    }
}