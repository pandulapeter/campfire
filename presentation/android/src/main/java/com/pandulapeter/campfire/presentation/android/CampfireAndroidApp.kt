package com.pandulapeter.campfire.presentation.android

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.android.catalogue.CampfireAndroidTheme
import com.pandulapeter.campfire.presentation.android.screens.CollectionsScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.HomeScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.PlaylistsScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.SettingsScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.SongsScreenAndroid
import com.pandulapeter.campfire.presentation.android.utilities.keyboardState
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.utilities.UiSize
import org.koin.java.KoinJavaComponent.get

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CampfireAndroidApp(
    activity: Activity,
    viewModel: CampfireViewModel = get(CampfireViewModel::class.java),
    windowSizeClass: WindowSizeClass = calculateWindowSizeClass(activity)
) {
    val uiMode = viewModel.uiMode.collectAsState(null)
    val uiSize = when {
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact -> UiSize.COMPACT
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded && windowSizeClass.heightSizeClass == WindowHeightSizeClass.Expanded -> UiSize.EXPANDED
        else -> UiSize.MEDIUM
    }
    val isKeyboardVisible = keyboardState()

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
                if (uiSize == UiSize.COMPACT) {
                    CampfireAppBar(
                        selectedNavigationDestination = navigationDestinations.value.firstOrNull { it.isSelected }?.destination
                    )
                }
            },
            bottomBar = {
                if (uiSize == UiSize.COMPACT) {
                    CampfireBottomNavigation(
                        modifier = Modifier
                            .imePadding()
                            .navigationBarsPadding(),
                        navigationDestinations = navigationDestinations.value,
                        onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                        isKeyboardVisible = isKeyboardVisible.value
                    )
                }
            }
        ) { scaffoldPadding ->
            when (uiSize) {
                UiSize.COMPACT -> {
                    Content(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding)
                            .consumeWindowInsets(scaffoldPadding)
                            .systemBarsPadding(),
                        selectedNavigationDestination = selectedNavigationDestination.value,
                        shouldUseExpandedUi = false
                    )
                }
                UiSize.MEDIUM -> {
                    CampfireNavigationRail(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding)
                            .consumeWindowInsets(scaffoldPadding)
                            .systemBarsPadding(),
                        navigationDestinations = navigationDestinations.value,
                        onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                        isKeyboardVisible = isKeyboardVisible.value,
                        content = {
                            Content(
                                selectedNavigationDestination = selectedNavigationDestination.value,
                                shouldUseExpandedUi = true
                            )
                        }
                    )
                }
                UiSize.EXPANDED -> {
                    CampfirePermanentNavigationDrawer(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding)
                            .consumeWindowInsets(scaffoldPadding)
                            .systemBarsPadding(),
                        navigationDestinations = navigationDestinations.value,
                        onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                        content = {
                            Content(
                                selectedNavigationDestination = selectedNavigationDestination.value,
                                shouldUseExpandedUi = true
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Content(
    modifier: Modifier = Modifier,
    selectedNavigationDestination: CampfireViewModel.NavigationDestination?,
    shouldUseExpandedUi: Boolean
) = Crossfade(
    modifier = modifier,
    targetState = selectedNavigationDestination
) { destination ->
    when (destination) {
        CampfireViewModel.NavigationDestination.HOME -> HomeScreenAndroid(shouldUseExpandedUi = shouldUseExpandedUi)
        CampfireViewModel.NavigationDestination.COLLECTIONS -> CollectionsScreenAndroid()
        CampfireViewModel.NavigationDestination.SONGS -> SongsScreenAndroid()
        CampfireViewModel.NavigationDestination.PLAYLISTS -> PlaylistsScreenAndroid()
        CampfireViewModel.NavigationDestination.SETTINGS -> SettingsScreenAndroid()
        null -> Unit
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
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    isKeyboardVisible: Boolean
) = AnimatedVisibility(
    visible = !isKeyboardVisible
) {
    BottomNavigation(
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
}

@Composable
private fun CampfireNavigationRail(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    isKeyboardVisible: Boolean,
    content: @Composable () -> Unit
) = Row(
    modifier = modifier
) {
    AnimatedVisibility(visible = !isKeyboardVisible) {
        NavigationRail {
            navigationDestinations.forEach { navigationDestination ->
                NavigationRailItem(
                    selected = navigationDestination.isSelected,
                    onClick = { onNavigationDestinationSelected(navigationDestination.destination) },
                    icon = {
                        Icon(
                            imageVector = navigationDestination.destination.icon,
                            contentDescription = navigationDestination.destination.displayName,
                        )
                    },
                    label = { Text(navigationDestination.destination.displayName) }
                )
            }
        }
    }
    content()
}

@Composable
private fun CampfirePermanentNavigationDrawer(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    content: @Composable () -> Unit
) = PermanentNavigationDrawer(
    modifier = modifier,
    drawerContent = {
        PermanentDrawerSheet {
            navigationDestinations.forEach { navigationDestination ->
                NavigationDrawerItem(
                    selected = navigationDestination.isSelected,
                    onClick = { onNavigationDestinationSelected(navigationDestination.destination) },
                    icon = {
                        Icon(
                            imageVector = navigationDestination.destination.icon,
                            contentDescription = navigationDestination.destination.displayName,
                        )
                    },
                    label = { Text(navigationDestination.destination.displayName) }
                )
            }
        }
    },
    content = content
)