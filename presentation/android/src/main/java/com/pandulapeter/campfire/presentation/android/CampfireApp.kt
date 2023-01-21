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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.lightColors
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import com.pandulapeter.campfire.shared.ui.TestUi
import com.pandulapeter.campfire.shared.ui.TestUiStateHolder
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

@OptIn(ExperimentalMaterialApi::class, ExperimentalLayoutApi::class)
@Composable
fun CampfireApp(
    stateHolder: TestUiStateHolder = get(TestUiStateHolder::class.java)
) = MaterialTheme(
    colors = lightColors(
        primary = colorResource(id = R.color.brand_orange),
        secondary = colorResource(id = R.color.brand_orange)
    )
) {
    Scaffold(
        modifier = Modifier
            .imePadding()
            .statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        modifier = Modifier.statusBarsPadding(),
                        color = MaterialTheme.colors.onPrimary,
                        text = "Campfire"
                    )
                }
            )
        },
        bottomBar = {
            BottomNavigation(
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                BottomNavigationItem(
                    selected = true,
                    onClick = {},
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                        )
                    }
                )
                BottomNavigationItem(
                    selected = false,
                    onClick = {},
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Playlists",
                        )
                    }
                )
                BottomNavigationItem(
                    selected = false,
                    onClick = {},
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                )
            }
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
                        //  contentPadding = LocalWindowInsets.current.systemBars.toPaddingValues(),
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