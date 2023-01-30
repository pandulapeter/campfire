package com.pandulapeter.campfire.presentation.android

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.pandulapeter.campfire.presentation.android.catalogue.CampfireAndroidTheme
import com.pandulapeter.campfire.presentation.android.screens.SetlistsScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.SettingsScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.SongsScreenAndroid
import com.pandulapeter.campfire.presentation.android.utilities.keyboardState
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireBottomNavigationBar
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireNavigationRail
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireScaffold
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import org.koin.java.KoinJavaComponent.get

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CampfireAndroidApp(
    viewModel: CampfireViewModel = get(CampfireViewModel::class.java),
    stateHolder: CampfireViewModelStateHolder,
) {
    LaunchedEffect(Unit) { viewModel.onInitialize() }

    val isKeyboardVisible = keyboardState()
    val songsScreenScrollState = rememberLazyListState()
    val songsScreenPullRefreshState = rememberPullRefreshState(
        refreshing = stateHolder.isRefreshing.value,
        onRefresh = stateHolder::onForceRefreshTriggered
    )
    val shouldUseExpandedUi = LocalConfiguration.current.screenWidthDp > 720

    CampfireAndroidTheme(
        uiMode = stateHolder.uiMode.value
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.surface),
            elevation = AppBarDefaults.TopAppBarElevation,
            content = {}
        )
        CampfireScaffold(
            modifier = Modifier.safeContentPadding(),
            uiStrings = stateHolder.uiStrings.value,
            navigationDestinations = stateHolder.navigationDestinations.value,
            isInLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
            appBarActions = {
                if (!shouldUseExpandedUi) {
                    // TODO: Add filters
                }
            },
            bottomNavigationBar = {
                BottomNavigationBarWrapper(
                    modifier = Modifier,
                    navigationDestinations = stateHolder.navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                    isKeyboardVisible = isKeyboardVisible.value,
                    uiStrings = stateHolder.uiStrings.value
                )
            },
            navigationRail = { scaffoldPadding, content ->
                NavigationRailWrapper(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                        .systemBarsPadding(),
                    navigationDestinations = stateHolder.navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                    isKeyboardVisible = isKeyboardVisible.value,
                    uiStrings = stateHolder.uiStrings.value,
                    content = content
                )
            },
            content = { scaffoldPadding ->
                Content(
                    modifier = scaffoldPadding?.let { Modifier.padding(it) } ?: Modifier,
                    stateHolder = stateHolder,
                    selectedNavigationDestination = stateHolder.selectedNavigationDestination.value,
                    shouldUseExpandedUi = shouldUseExpandedUi,
                    songsScreenScrollState = songsScreenScrollState,
                    songsScreenPullRefreshState = songsScreenPullRefreshState
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun Content(
    modifier: Modifier = Modifier,
    selectedNavigationDestination: CampfireViewModel.NavigationDestination?,
    stateHolder: CampfireViewModelStateHolder,
    shouldUseExpandedUi: Boolean,
    songsScreenPullRefreshState: PullRefreshState,
    songsScreenScrollState: LazyListState
) = Crossfade(
    modifier = modifier.fillMaxSize(),
    targetState = selectedNavigationDestination
) { destination ->
    when (destination) {
        CampfireViewModel.NavigationDestination.SONGS -> SongsScreenAndroid(
            stateHolder = stateHolder,
            shouldUseExpandedUi = shouldUseExpandedUi,
            pullRefreshState = songsScreenPullRefreshState,
            lazyListState = songsScreenScrollState
        )
        CampfireViewModel.NavigationDestination.SETLISTS -> SetlistsScreenAndroid(
            stateHolder = stateHolder
        )
        CampfireViewModel.NavigationDestination.SETTINGS -> SettingsScreenAndroid(
            stateHolder = stateHolder
        )
        null -> Unit
    }
}

@Composable
private fun BottomNavigationBarWrapper(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    isKeyboardVisible: Boolean
) = AnimatedVisibility(
    modifier = modifier,
    visible = !isKeyboardVisible
) {
    CampfireBottomNavigationBar(
        uiStrings = uiStrings,
        navigationDestinations = navigationDestinations,
        onNavigationDestinationSelected = onNavigationDestinationSelected
    )
}

@Composable
private fun NavigationRailWrapper(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    uiStrings: CampfireStrings,
    isKeyboardVisible: Boolean,
    content: @Composable () -> Unit
) = Row(
    modifier = modifier
) {
    AnimatedVisibility(visible = !isKeyboardVisible) {
        CampfireNavigationRail(
            navigationDestinations = navigationDestinations,
            onNavigationDestinationSelected = onNavigationDestinationSelected,
            uiStrings = uiStrings
        )
    }
    content()
}