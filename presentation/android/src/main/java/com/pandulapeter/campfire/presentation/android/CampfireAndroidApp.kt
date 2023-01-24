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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.android.catalogue.CampfireAndroidTheme
import com.pandulapeter.campfire.presentation.android.screens.SongsScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.PlaylistsScreenAndroid
import com.pandulapeter.campfire.presentation.android.screens.SettingsScreenAndroid
import com.pandulapeter.campfire.presentation.android.utilities.keyboardState
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireBottomNavigationBar
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireNavigationRail
import com.pandulapeter.campfire.shared.ui.catalogue.components.CampfireScaffold
import org.koin.java.KoinJavaComponent.get

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampfireAndroidApp(
    viewModel: CampfireViewModel = get(CampfireViewModel::class.java)
) {
    val uiMode = viewModel.uiMode.collectAsState(null)
    val userPreferences = viewModel.userPreferences.collectAsState(null)
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
            isInLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
            userPreferences = userPreferences.value,
            bottomNavigationBar = {
                BottomNavigationBarWrapper(
                    modifier = Modifier
                        .imePadding()
                        .navigationBarsPadding(),
                    navigationDestinations = navigationDestinations.value,
                    onNavigationDestinationSelected = viewModel::onNavigationDestinationSelected,
                    isKeyboardVisible = isKeyboardVisible.value,
                    userPreferences = userPreferences.value
                )
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
                    userPreferences = userPreferences.value,
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
                    shouldUseExpandedUi = scaffoldPadding == null // TODO: Should be based on screen width
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
        CampfireViewModel.NavigationDestination.SONGS -> SongsScreenAndroid(shouldUseExpandedUi = shouldUseExpandedUi)
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