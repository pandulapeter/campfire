package com.pandulapeter.campfire.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.catalogue.CampfireDesktopTheme
import com.pandulapeter.campfire.presentation.screens.PlaylistsScreensDesktop
import com.pandulapeter.campfire.presentation.screens.SettingsScreensDesktop
import com.pandulapeter.campfire.presentation.screens.SongsScreenDesktop
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireBottomNavigationBar
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireNavigationRail
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireScaffold
import org.koin.java.KoinJavaComponent

@Composable
fun CampfireDesktopApp(
    viewModel: CampfireViewModel = KoinJavaComponent.get(CampfireViewModel::class.java),
    stateHolder: CampfireViewModelStateHolder,
    windowSize: DpSize
) {
    LaunchedEffect(Unit) { viewModel.onInitialize() }

    val songsScreenScrollState = rememberLazyListState()

    CampfireDesktopTheme(
        uiMode = stateHolder.uiMode.value
    ) {
        CampfireScaffold(
            navigationDestinations = stateHolder.navigationDestinations.value,
            isInLandscape = windowSize.width > windowSize.height,
            userPreferences = stateHolder.userPreferences.value,
            bottomNavigationBar = {
                CampfireBottomNavigationBar(
                    navigationDestinations = stateHolder.navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                    userPreferences = stateHolder.userPreferences.value
                )
            },
            navigationRail = { scaffoldPadding, content ->
                NavigationRailWrapper(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                    navigationDestinations = stateHolder.navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                    userPreferences = stateHolder.userPreferences.value,
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
                    stateHolder = stateHolder,
                    selectedNavigationDestination = stateHolder.selectedNavigationDestination.value,
                    shouldUseExpandedUi = scaffoldPadding == null, // TODO: Should be based on screen width
                    songsScreenScrollState = songsScreenScrollState
                )
            }
        )
    }
}

@Composable
private fun Content(
    modifier: Modifier = Modifier,
    selectedNavigationDestination: CampfireViewModel.NavigationDestination?,
    stateHolder: CampfireViewModelStateHolder,
    shouldUseExpandedUi: Boolean,
    songsScreenScrollState: LazyListState
) = Crossfade(
    modifier = modifier.fillMaxSize(),
    targetState = selectedNavigationDestination
) { destination ->
    when (destination) {
        CampfireViewModel.NavigationDestination.SONGS -> SongsScreenDesktop(
            stateHolder = stateHolder,
            shouldUseExpandedUi = shouldUseExpandedUi,
            lazyListState = songsScreenScrollState
        )
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