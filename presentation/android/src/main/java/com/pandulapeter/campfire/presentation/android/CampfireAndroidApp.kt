package com.pandulapeter.campfire.presentation.android

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.android.catalogue.CampfireAndroidTheme
import com.pandulapeter.campfire.presentation.android.screens.CollectionsScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.HomeScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.PlaylistsScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.SettingsScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.SongsScreenAndroid
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import org.koin.java.KoinJavaComponent.get

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampfireAndroidApp(
    viewModel: CampfireViewModel = get(CampfireViewModel::class.java)
) {
    val uiMode = viewModel.uiMode.collectAsState(null)

    CampfireAndroidTheme(
        uiMode = uiMode.value,
        shouldUseDynamicColors = true // TODO: Read from UserPreferences
    ) {
        val selectedNavigationDestination = viewModel.selectedNavigationDestination.collectAsState(initial = null)
        val navigationDestinations = viewModel.navigationDestinations.collectAsState(initial = emptyList())

        Scaffold(
            modifier = Modifier
                .imePadding()
                .statusBarsPadding(),
            topBar = {
                CampfireAppBar(
                    selectedNavigationDestination = navigationDestinations.value.firstOrNull { it.isSelected }?.destination
                )
            },
            bottomBar = {
                CampfireBottomNavigation(
                    modifier = Modifier
                        .imePadding()
                        .navigationBarsPadding(),
                    navigationDestinations = navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected
                )
            }
        ) { scaffoldPadding ->
            Crossfade(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .consumeWindowInsets(scaffoldPadding)
                    .systemBarsPadding(),
                targetState = selectedNavigationDestination.value
            ) { selectedNavigationDestination ->
                when (selectedNavigationDestination) {
                    CampfireViewModel.NavigationDestination.HOME -> HomeScreenAndroid()
                    CampfireViewModel.NavigationDestination.COLLECTIONS -> CollectionsScreenAndroid()
                    CampfireViewModel.NavigationDestination.SONGS -> SongsScreenAndroid()
                    CampfireViewModel.NavigationDestination.PLAYLISTS -> PlaylistsScreenAndroid()
                    CampfireViewModel.NavigationDestination.SETTINGS -> SettingsScreenAndroid()
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun CampfireAppBar(
    modifier: Modifier = Modifier,
    selectedNavigationDestination: CampfireViewModel.NavigationDestination?
) = TopAppBar(
    modifier = modifier,
    backgroundColor = MaterialTheme.colors.surface,
    title = {
        Text(
            modifier = Modifier.statusBarsPadding(),
            text = selectedNavigationDestination?.displayName ?: "Campfire"
        )
    }
)

@Composable
private fun CampfireBottomNavigation(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit
) = BottomNavigation(
    modifier = modifier,
    backgroundColor = MaterialTheme.colors.surface
) {
    navigationDestinations.forEach { navigationDestination ->
        BottomNavigationItem(
            selected = navigationDestination.isSelected,
            onClick = { onNavigationDestinationSelected(navigationDestination.destination) },
            icon = {
                Icon(
                    imageVector = navigationDestination.destination.icon,
                    contentDescription = navigationDestination.destination.displayName,
                )
            }
        )
    }
}