package com.pandulapeter.campfire.shared.ui.catalogue.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.SongDetails
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireIcons
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import com.pandulapeter.campfire.shared.ui.catalogue.theme.CampfireColors

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CampfireScaffold(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    modalBottomSheetState: ModalBottomSheetState,
    songDetails: SongDetails?,
    songDetailsScrollState: ScrollState,
    onSongClosed: () -> Unit,
    query: String,
    onQueryChanged: (String) -> Unit,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    isInLandscape: Boolean,
    shouldUseExpandedUi: Boolean,
    appBarActions: @Composable RowScope.() -> Unit = {},
    bottomNavigationBar: @Composable () -> Unit,
    navigationRail: @Composable (scaffoldPadding: PaddingValues, content: @Composable () -> Unit) -> Unit,
    content: @Composable (scaffoldPadding: PaddingValues?) -> Unit
) = ModalBottomSheetLayout(
    sheetState = modalBottomSheetState,
    scrimColor = Color.Transparent,
    sheetContent = {
        if (songDetails == null) {
            CircularProgressIndicator(
                modifier = Modifier.padding(16.dp)
            )
        } else {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onSongClosed
                    ) {
                        Icon(
                            imageVector = CampfireIcons.close,
                            contentDescription = uiStrings.songsClose
                        )
                    }
                },
                title = { Text(text = songDetails.song.title) }
            )
            Text(
                modifier = Modifier.verticalScroll(songDetailsScrollState).padding(16.dp),
                text = songDetails.rawData
            )
        }
    }
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            val selectedNavigationDestination = navigationDestinations.firstOrNull { it.isSelected }?.destination
            CampfireAppBar(
                uiStrings = uiStrings,
                selectedNavigationDestination = selectedNavigationDestination,
                actions = {
                    appBarActions()
                    Spacer(modifier = Modifier.width(8.dp))
                    AnimatedVisibility(
                        visible = selectedNavigationDestination == CampfireViewModel.NavigationDestination.SONGS
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SearchItem(
                                modifier = Modifier.width(160.dp).padding(vertical = 8.dp),
                                uiStrings = uiStrings,
                                query = query,
                                onQueryChanged = onQueryChanged
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (!shouldUseExpandedUi) {
                                IconButton(
                                    onClick = {}, //TODO: Bring up the Sorting UI
                                ) {
                                    Icon(
                                        imageVector = CampfireIcons.sort,
                                        contentDescription = uiStrings.songsSortingMode
                                    )
                                }
                                IconButton(
                                    onClick = {}, //TODO: Bring up the Filtering UI
                                ) {
                                    Icon(
                                        imageVector = CampfireIcons.filter,
                                        contentDescription = uiStrings.songsFilters
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isInLandscape) {
                bottomNavigationBar()
            }
        },
        content = { scaffoldPadding ->
            if (isInLandscape) {
                navigationRail(scaffoldPadding) { content(null) }
            } else {
                content(scaffoldPadding)
            }
        }
    )
}

@Composable
fun CampfireAppBar(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    selectedNavigationDestination: CampfireViewModel.NavigationDestination?,
    actions: @Composable RowScope.() -> Unit
) = TopAppBar(
    modifier = modifier,
    actions = actions,
    backgroundColor = MaterialTheme.colors.background,
    title = {
        Text(
            text = when (selectedNavigationDestination) {
                CampfireViewModel.NavigationDestination.SONGS -> uiStrings.songs
                CampfireViewModel.NavigationDestination.SETLISTS -> uiStrings.setlists
                CampfireViewModel.NavigationDestination.SETTINGS -> uiStrings.settings
                null -> ""
            }
        )
    }
)

@Composable
fun CampfireNavigationRail(
    modifier: Modifier = Modifier,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit,
    uiStrings: CampfireStrings
) = NavigationRail(
    modifier = modifier
) {
    navigationDestinations.forEach { navigationDestination ->
        NavigationRailItem(
            selected = navigationDestination.isSelected,
            onClick = { onNavigationDestinationSelected(navigationDestination.destination) },
            selectedContentColor = MaterialTheme.colors.primary,
            unselectedContentColor = CampfireColors.getUnselectedContentColor(),
            icon = {
                Icon(
                    imageVector = navigationDestination.destination.icon,
                    contentDescription = when (navigationDestination.destination) {
                        CampfireViewModel.NavigationDestination.SONGS -> uiStrings.songs
                        CampfireViewModel.NavigationDestination.SETLISTS -> uiStrings.setlists
                        CampfireViewModel.NavigationDestination.SETTINGS -> uiStrings.settings
                    }
                )
            }
        )
    }
}


@Composable
fun CampfireBottomNavigationBar(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    onNavigationDestinationSelected: (CampfireViewModel.NavigationDestination) -> Unit
) = BottomAppBar(
    modifier = modifier,
    backgroundColor = MaterialTheme.colors.surface
) {
    navigationDestinations.forEach { navigationDestination ->
        BottomNavigationItem(
            selected = navigationDestination.isSelected,
            onClick = { onNavigationDestinationSelected(navigationDestination.destination) },
            selectedContentColor = MaterialTheme.colors.primary,
            unselectedContentColor = CampfireColors.getUnselectedContentColor(),
            icon = {
                Icon(
                    imageVector = navigationDestination.destination.icon,
                    contentDescription = when (navigationDestination.destination) {
                        CampfireViewModel.NavigationDestination.SONGS -> uiStrings.songs
                        CampfireViewModel.NavigationDestination.SETLISTS -> uiStrings.setlists
                        CampfireViewModel.NavigationDestination.SETTINGS -> uiStrings.settings
                    }
                )
            }
        )
    }
}