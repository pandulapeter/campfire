package com.pandulapeter.campfire.presentation.android

import android.content.res.Configuration
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.android.catalogue.CampfireAndroidTheme
import com.pandulapeter.campfire.presentation.android.screens.PlaylistsScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.SettingsScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.SongsScreenAndroid
import com.pandulapeter.campfire.presentation.android.utilities.keyboardState
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireBottomNavigationBar
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireNavigationRail
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireScaffold
import org.koin.java.KoinJavaComponent.get

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterialApi::class)
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

    CampfireAndroidTheme(
        uiMode = stateHolder.uiMode.value
    ) {
        CampfireScaffold(
            modifier = Modifier
                .imePadding()
                .statusBarsPadding(),
            statusBarModifier = Modifier.statusBarsPadding(),
            navigationDestinations = stateHolder.navigationDestinations.value,
            isInLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
            userPreferences = stateHolder.userPreferences.value,
            bottomNavigationBar = {
                BottomNavigationBarWrapper(
                    modifier = Modifier
                        .imePadding()
                        .navigationBarsPadding(),
                    navigationDestinations = stateHolder.navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                    isKeyboardVisible = isKeyboardVisible.value,
                    userPreferences = stateHolder.userPreferences.value
                )
            },
            navigationRail = { scaffoldPadding, content ->
                NavigationRailWrapper(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                        .consumeWindowInsets(scaffoldPadding)
                        .systemBarsPadding(),
                    navigationDestinations = stateHolder.navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                    isKeyboardVisible = isKeyboardVisible.value,
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
                            .consumeWindowInsets(scaffoldPadding)
                            .systemBarsPadding()
                    } ?: Modifier,
                    stateHolder = stateHolder,
                    selectedNavigationDestination = stateHolder.selectedNavigationDestination.value,
                    shouldUseExpandedUi = scaffoldPadding == null, // TODO: Should be based on screen width
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
        CampfireViewModel.NavigationDestination.PLAYLISTS -> PlaylistsScreenAndroid()
        CampfireViewModel.NavigationDestination.SETTINGS -> SettingsScreenAndroid()
        null -> Unit
    }
}

@Composable
private fun BottomNavigationBarWrapper(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    isKeyboardVisible: Boolean,
    userPreferences: UserPreferences?
) = AnimatedVisibility(
    modifier = modifier,
    visible = !isKeyboardVisible
) {
    CampfireBottomNavigationBar(
        navigationDestinations = navigationDestinations,
        onNavigationDestinationSelected = onNavigationDestinationSelected,
        userPreferences = userPreferences
    )
}

@Composable
private fun NavigationRailWrapper(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    userPreferences: UserPreferences?,
    isKeyboardVisible: Boolean,
    content: @Composable () -> Unit
) = Row(
    modifier = modifier
) {
    AnimatedVisibility(visible = !isKeyboardVisible) {
        CampfireNavigationRail(
            navigationDestinations = navigationDestinations,
            onNavigationDestinationSelected = onNavigationDestinationSelected,
            userPreferences = userPreferences
        )
    }
    content()
}