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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireBottomNavigationBar
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireNavigationRail
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireScaffold
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
    val uiSize = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> UiSize.COMPACT
        else -> UiSize.EXPANDED
    }
    val isKeyboardVisible = keyboardState()

    LaunchedEffect(Unit) { viewModel.onInitialize() }

    CampfireAndroidTheme(
        uiMode = uiMode.value
    ) {
        val selectedNavigationDestination = viewModel.selectedNavigationDestination.collectAsState(initial = null)
        val navigationDestinations = viewModel.navigationDestinations.collectAsState(initial = emptyList())

        CampfireScaffold(
            modifier = Modifier
                .imePadding()
                .statusBarsPadding(),
            statusBarModifier = Modifier.statusBarsPadding(),
            navigationDestinations = navigationDestinations.value,
            uiSize = uiSize,
            bottomNavigationBar = {
                if (uiSize == UiSize.COMPACT) {
                    BottomNavigationBarWrapper(
                        modifier = Modifier
                            .imePadding()
                            .navigationBarsPadding(),
                        navigationDestinations = navigationDestinations.value,
                        onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                        isKeyboardVisible = isKeyboardVisible.value
                    )
                }
            },
            navigationRail = { scaffoldPadding, content ->
                NavigationRailWrapper(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                        .consumeWindowInsets(scaffoldPadding)
                        .systemBarsPadding(),
                    navigationDestinations = navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                    isKeyboardVisible = isKeyboardVisible.value,
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
                    selectedNavigationDestination = selectedNavigationDestination.value,
                    shouldUseExpandedUi = scaffoldPadding == null
                )
            }
        )
    }
}

@Composable
private fun Content(
    modifier: Modifier = Modifier,
    selectedNavigationDestination: CampfireViewModel.NavigationDestination?,
    shouldUseExpandedUi: Boolean
) = Crossfade(
    modifier = modifier.fillMaxSize(),
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
private fun BottomNavigationBarWrapper(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    isKeyboardVisible: Boolean
) = AnimatedVisibility(
    modifier = modifier,
    visible = !isKeyboardVisible
) {
    CampfireBottomNavigationBar(
        navigationDestinations = navigationDestinations,
        onNavigationDestinationSelected = onNavigationDestinationSelected
    )
}

@Composable
private fun NavigationRailWrapper(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    isKeyboardVisible: Boolean,
    content: @Composable () -> Unit
) = Row(
    modifier = modifier
) {
    AnimatedVisibility(visible = !isKeyboardVisible) {
        CampfireNavigationRail(
            navigationDestinations = navigationDestinations,
            onNavigationDestinationSelected = onNavigationDestinationSelected
        )
    }
    content()
}