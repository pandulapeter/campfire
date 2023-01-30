package com.pandulapeter.campfire.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.catalogue.CampfireDesktopTheme
import com.pandulapeter.campfire.presentation.screens.SetlistsScreensDesktop
import com.pandulapeter.campfire.presentation.screens.SettingsScreensDesktop
import com.pandulapeter.campfire.presentation.screens.SongsScreenDesktop
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireBottomNavigationBar
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireNavigationRail
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireScaffold
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import org.koin.java.KoinJavaComponent

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CampfireDesktopApp(
    viewModel: CampfireViewModel = KoinJavaComponent.get(CampfireViewModel::class.java),
    stateHolder: CampfireViewModelStateHolder,
    windowSize: DpSize
) {
    LaunchedEffect(Unit) { viewModel.onInitialize() }

    val songsScreenScrollState = rememberLazyListState()
    val shouldUseExpandedUi = windowSize.width > 720.dp

    CampfireDesktopTheme(
        uiMode = stateHolder.uiMode.value
    ) {
        CampfireScaffold(
            navigationDestinations = stateHolder.navigationDestinations.value,
            uiStrings = stateHolder.uiStrings.value,
            query = stateHolder.query.value,
            onQueryChanged = stateHolder::onQueryChanged,
            isInLandscape = windowSize.width > windowSize.height,
            shouldUseExpandedUi = shouldUseExpandedUi,
            appBarActions = {
                AnimatedVisibility(
                    visible = stateHolder.isRefreshing.value,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            },
            bottomNavigationBar = {
                CampfireBottomNavigationBar(
                    uiStrings = stateHolder.uiStrings.value,
                    navigationDestinations = stateHolder.navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected
                )
            },
            navigationRail = { scaffoldPadding, content ->
                NavigationRailWrapper(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                    navigationDestinations = stateHolder.navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                    uiStrings = stateHolder.uiStrings.value,
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
                    shouldUseExpandedUi = shouldUseExpandedUi,
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
        CampfireViewModel.NavigationDestination.SETLISTS -> SetlistsScreensDesktop(
            stateHolder = stateHolder
        )
        CampfireViewModel.NavigationDestination.SETTINGS -> SettingsScreensDesktop(
            stateHolder = stateHolder
        )
        null -> Unit
    }
}

@Composable
private fun NavigationRailWrapper(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    uiStrings: CampfireStrings,
    content: @Composable () -> Unit
) = Row(
    modifier = modifier
) {
    CampfireNavigationRail(
        navigationDestinations = navigationDestinations,
        onNavigationDestinationSelected = onNavigationDestinationSelected,
        uiStrings = uiStrings
    )
    content()
}