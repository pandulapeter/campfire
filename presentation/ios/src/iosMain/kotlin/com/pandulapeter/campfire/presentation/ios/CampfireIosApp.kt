package com.pandulapeter.campfire.presentation.ios

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.ios.catalogue.CampfireIosTheme
import com.pandulapeter.campfire.presentation.ios.screens.SetlistsScreenIos
import com.pandulapeter.campfire.presentation.ios.screens.SettingsScreenIos
import com.pandulapeter.campfire.presentation.ios.screens.SongsScreenIos
import com.pandulapeter.campfire.presentation.ios.utilities.keyboardState
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireBottomNavigationBar
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireNavigationRail
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireScaffold
import com.pandulapeter.campfire.shared.ui.catalogue.components.UiSize
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import org.koin.mp.KoinPlatform

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CampfireIosApp(
    viewModel: CampfireViewModel = KoinPlatform.getKoin().get(),
    stateHolder: CampfireViewModelStateHolder,
    urlOpener: (String) -> Unit
) = BoxWithConstraints {
    LaunchedEffect(Unit) { viewModel.onInitialize() }

    val isKeyboardVisible = keyboardState()
    val songsScreenPullRefreshState = rememberPullRefreshState(
        refreshing = stateHolder.isRefreshing.value,
        onRefresh = stateHolder::onForceRefreshTriggered
    )
    val uiSize = UiSize.fromScreenWidth(maxWidth)

    CampfireIosTheme(
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
            modifier = Modifier
                .systemBarsPadding()
                .imePadding(),
            uiStrings = stateHolder.uiStrings.value,
            rawSongDetailsMap = stateHolder.rawSongDetails.value,
            onSongClosed = stateHolder::onSongClosed,
            stateHolder = stateHolder,
            query = stateHolder.query.value,
            onQueryChanged = stateHolder::onQueryChanged,
            navigationDestinations = stateHolder.navigationDestinations.value,
            uiSize = uiSize,
            bottomNavigationBar = {
                BottomNavigationBarWrapper(
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
                    uiSize = uiSize,
                    songsScreenScrollState = stateHolder.songsScreenScrollState,
                    setlistsScreenScrollState = stateHolder.setlistsScreenScrollState,
                    songsScreenPullRefreshState = songsScreenPullRefreshState,
                    urlOpener = urlOpener
                )
            },
            urlOpener = urlOpener
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun Content(
    modifier: Modifier = Modifier,
    selectedNavigationDestination: CampfireViewModel.NavigationDestination?,
    stateHolder: CampfireViewModelStateHolder,
    uiSize: UiSize,
    songsScreenPullRefreshState: PullRefreshState,
    songsScreenScrollState: LazyListState,
    setlistsScreenScrollState: LazyListState,
    urlOpener: (String) -> Unit
) = Crossfade(
    modifier = modifier.fillMaxSize(),
    targetState = selectedNavigationDestination,
    label = "mainNavigation"
) { destination ->
    when (destination) {
        CampfireViewModel.NavigationDestination.SONGS -> SongsScreenIos(
            stateHolder = stateHolder,
            shouldUseExpandedUi = uiSize.shouldUseExpandedUi,
            pullRefreshState = songsScreenPullRefreshState,
            lazyListState = songsScreenScrollState
        )

        CampfireViewModel.NavigationDestination.SETLISTS -> SetlistsScreenIos(
            stateHolder = stateHolder,
            state = setlistsScreenScrollState,
            shouldUseExpandedUi = uiSize.shouldUseExpandedUi
        )

        CampfireViewModel.NavigationDestination.SETTINGS -> SettingsScreenIos(
            stateHolder = stateHolder,
            urlOpener = urlOpener
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
) {
    if (!isKeyboardVisible) {
        CampfireBottomNavigationBar(
            modifier = modifier,
            uiStrings = uiStrings,
            navigationDestinations = navigationDestinations,
            onNavigationDestinationSelected = onNavigationDestinationSelected
        )
    }
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
