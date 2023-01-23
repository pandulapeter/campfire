package com.pandulapeter.campfire.presentation

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.catalogue.CampfireDesktopTheme
import com.pandulapeter.campfire.shared.ui.TestUi
import com.pandulapeter.campfire.shared.ui.TestUiStateHolder
import org.koin.java.KoinJavaComponent

@Composable
fun CampfireDesktopApp(
    stateHolder: TestUiStateHolder = KoinJavaComponent.get(TestUiStateHolder::class.java)
) {
    val uiMode = stateHolder.uiMode.collectAsState(null)

    CampfireDesktopTheme(
        uiMode = uiMode.value
    ) {
        val navigationDestinations = stateHolder.navigationDestinations.collectAsState(initial = emptyList())

        Row(
            modifier = Modifier.background(MaterialTheme.colors.background)
        ) {
            CampfireNavigationRail(
                navigationDestinations = navigationDestinations.value,
                onNavigationDestinationSelected = stateHolder::onNavigationDestinationSelected
            )
            TestUi(
                lazyColumnWrapper = {
                    val state = rememberLazyListState()

                    LazyColumn(
                        modifier = Modifier.fillMaxHeight().padding(end = 8.dp),
                        state = state,
                        content = it
                    )
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(
                            scrollState = state
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun CampfireNavigationRail(
    modifier: Modifier = Modifier,
    navigationDestinations: List<TestUiStateHolder.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (TestUiStateHolder.NavigationDestination) -> Unit
) = NavigationRail(
    modifier = modifier
) {
    navigationDestinations.forEach { navigationDestination ->
        NavigationRailItem(
            selected = navigationDestination.isSelected,
            onClick = { onNavigationDestinationSelected(navigationDestination.destination) },
            label = {
                Text(navigationDestination.destination.displayName)
            },
            icon = {
                Icon(
                    imageVector = navigationDestination.destination.icon,
                    contentDescription = navigationDestination.destination.displayName,
                )
            }
        )
    }
}