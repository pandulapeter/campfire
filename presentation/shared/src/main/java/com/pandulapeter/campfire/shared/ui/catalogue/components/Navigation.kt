package com.pandulapeter.campfire.shared.ui.catalogue.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import com.pandulapeter.campfire.shared.ui.catalogue.theme.CampfireColors

@Composable
fun CampfireScaffold(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    isInLandscape: Boolean,
    appBarActions: @Composable RowScope.() -> Unit,
    bottomNavigationBar: @Composable () -> Unit,
    navigationRail: @Composable (scaffoldPadding: PaddingValues, content: @Composable () -> Unit) -> Unit,
    content: @Composable (scaffoldPadding: PaddingValues?) -> Unit
) = Scaffold(
    modifier = modifier,
    topBar = {
        CampfireAppBar(
            uiStrings = uiStrings,
            selectedNavigationDestination = navigationDestinations.firstOrNull { it.isSelected }?.destination,
            actions = appBarActions
        )
    },
    bottomBar = {
        if (!isInLandscape) {
            bottomNavigationBar()
        }
    },
    content = { scaffoldPadding ->
        if (isInLandscape) {
            navigationRail(scaffoldPadding) { content(null) }
        } else {
            content(scaffoldPadding)
        }
    }
)

@Composable
fun CampfireAppBar(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    selectedNavigationDestination: CampfireViewModel.NavigationDestination?,
    actions: @Composable RowScope.() -> Unit
) = TopAppBar(
    modifier = modifier,
    actions = actions,
    backgroundColor = MaterialTheme.colors.background,
    title = {
        Text(
            text = when (selectedNavigationDestination) {
                CampfireViewModel.NavigationDestination.SONGS -> uiStrings.songs
                CampfireViewModel.NavigationDestination.SETLISTS -> uiStrings.setlists
                CampfireViewModel.NavigationDestination.SETTINGS -> uiStrings.settings
                null -> ""
            }
        )
    }
)

@Composable
fun CampfireNavigationRail(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    uiStrings: CampfireStrings
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
                    contentDescription = when (navigationDestination.destination) {
                        CampfireViewModel.NavigationDestination.SONGS -> uiStrings.songs
                        CampfireViewModel.NavigationDestination.SETLISTS -> uiStrings.setlists
                        CampfireViewModel.NavigationDestination.SETTINGS -> uiStrings.settings
                    }
                )
            }
        )
    }
}


@Composable
fun CampfireBottomNavigationBar(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit
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
                    contentDescription = when (navigationDestination.destination) {
                        CampfireViewModel.NavigationDestination.SONGS -> uiStrings.songs
                        CampfireViewModel.NavigationDestination.SETLISTS -> uiStrings.setlists
                        CampfireViewModel.NavigationDestination.SETTINGS -> uiStrings.settings
                    }
                )
            }
        )
    }
}