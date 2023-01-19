package com.pandulapeter.campfire.presentation.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.shared.ui.TestUi
import com.pandulapeter.campfire.shared.ui.TestUiStateHolder
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CampfireApp(
    stateHolder: TestUiStateHolder = get(TestUiStateHolder::class.java)
) = TestUi(
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