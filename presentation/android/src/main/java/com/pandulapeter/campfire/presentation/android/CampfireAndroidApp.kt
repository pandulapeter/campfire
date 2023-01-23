package com.pandulapeter.campfire.presentation.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.android.catalogue.CampfireAndroidTheme
import com.pandulapeter.campfire.shared.ui.TestUi
import com.pandulapeter.campfire.shared.ui.TestUiStateHolder
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

@OptIn(ExperimentalMaterialApi::class, ExperimentalLayoutApi::class)
@Composable
fun CampfireAndroidApp(
    stateHolder: TestUiStateHolder = get(TestUiStateHolder::class.java)
) {
    val uiMode = stateHolder.uiMode.collectAsState(null)

    CampfireAndroidTheme(
        uiMode = uiMode.value,
        shouldUseDynamicColors = true // TODO
    ) {
        val navigationDestinations = stateHolder.navigationDestinations.collectAsState(initial = emptyList())

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
                    onNavigationDestinationSelected = stateHolder::onNavigationDestinationSelected
                )
            }
        ) { scaffoldPadding ->
            TestUi(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .consumeWindowInsets(scaffoldPadding)
                    .systemBarsPadding(),
                lazyColumnWrapper = {
                    val isRefreshing = stateHolder.shouldShowLoadingIndicator.collectAsState(false)
                    val coroutineScope = rememberCoroutineScope()
                    val pullRefreshState = rememberPullRefreshState(
                        refreshing = isRefreshing.value,
                        onRefresh = { coroutineScope.launch { stateHolder.onForceRefreshPressed() } }
                    )

                    Box(
                        modifier = Modifier.pullRefresh(pullRefreshState)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxHeight(),
                            content = it
                        )
                        PullRefreshIndicator(
                            modifier = Modifier.align(Alignment.TopCenter),
                            refreshing = isRefreshing.value,
                            state = pullRefreshState
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun CampfireAppBar(
    modifier: Modifier = Modifier,
    selectedNavigationDestination: TestUiStateHolder.NavigationDestination?
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
    navigationDestinations: List<TestUiStateHolder.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (TestUiStateHolder.NavigationDestination) -> Unit
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