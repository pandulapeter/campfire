package com.pandulapeter.campfire.shared.ui.catalogue.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireIcons
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import com.pandulapeter.campfire.shared.ui.catalogue.theme.CampfireColors
import com.pandulapeter.campfire.shared.ui.screenComponents.shared.FilterControlsList
import com.pandulapeter.campfire.shared.ui.screenComponents.songs.SongsSortingControlsList
import dev.atsushieno.composempp.material.AlertDialog
import dev.atsushieno.composempp.material.DropdownMenu

@Composable
fun CampfireScaffold(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    rawSongDetailsMap: Map<String, RawSongDetails>?,
    onSongClosed: () -> Unit,
    query: String,
    onQueryChanged: (String) -> Unit,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    uiSize: UiSize,
    stateHolder: CampfireViewModelStateHolder,
    appBarActions: @Composable RowScope.() -> Unit = {},
    bottomNavigationBar: @Composable () -> Unit,
    navigationRail: @Composable (scaffoldPadding: PaddingValues, content: @Composable () -> Unit) -> Unit,
    content: @Composable (scaffoldPadding: PaddingValues?) -> Unit,
    urlOpener: (String) -> Unit
) = Box(
    modifier = modifier
) {
    val selectedNavigationDestination = navigationDestinations.firstOrNull { it.isSelected }?.destination
    Scaffold(
        scaffoldState = stateHolder.scaffoldState,
        topBar = {
            CampfireAppBar(
                stateHolder = stateHolder,
                uiStrings = uiStrings,
                shouldUseExpandedUi = uiSize.shouldUseExpandedUi,
                selectedNavigationDestination = selectedNavigationDestination,
                query = query,
                onQueryChanged = onQueryChanged,
                appBarActions = appBarActions,
            )
        },
        bottomBar = {
            if (!uiSize.shouldUseNavigationRail) {
                bottomNavigationBar()
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedNavigationDestination == CampfireViewModel.NavigationDestination.SETLISTS,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = stateHolder::onNewSetlistClicked
                ) {
                    Icon(
                        imageVector = CampfireIcons.add,
                        contentDescription = uiStrings.setlistsNewSetlist
                    )
                }
            }
        },
        content = { scaffoldPadding ->
            if (uiSize.shouldUseNavigationRail) {
                navigationRail(scaffoldPadding) { content(null) }
            } else {
                content(scaffoldPadding)
            }
        }
    )
    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visible = stateHolder.selectedSong.value != null
    ) {
        SongDetailsScreen(
            uiStrings = uiStrings,
            stateHolder = stateHolder,
            lazyListState = stateHolder.detailScreenCarouselState,
            songDetailsScreenData = stateHolder.selectedSong.value,
            rawSongDetailsMap = rawSongDetailsMap,
            setlists = stateHolder.setlists.value,
            onSongClosed = onSongClosed
        )
    }
    DynamicDialog(
        stateHolder = stateHolder,
        uiStrings = uiStrings,
        urlOpener = urlOpener
    )
}

enum class UiSize(private val size: Int) {
    COMPACT(1), MEDIUM(2), EXPANDED(3);

    val shouldUseExpandedUi get() = size >= EXPANDED.size

    val shouldUseNavigationRail get() = size >= MEDIUM.size

    companion object {
        fun fromScreenWidth(screenWidth: Dp) = if (screenWidth < 480.dp) {
            COMPACT
        } else if (screenWidth > 720.dp) {
            EXPANDED
        } else {
            MEDIUM
        }
    }
}

@Composable
fun CampfireAppBar(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    uiStrings: CampfireStrings,
    shouldUseExpandedUi: Boolean,
    selectedNavigationDestination: CampfireViewModel.NavigationDestination?,
    query: String,
    onQueryChanged: (String) -> Unit,
    appBarActions: @Composable RowScope.() -> Unit = {}
) = TopAppBar(
    modifier = modifier,
    actions = {
        appBarActions()
        if (!shouldUseExpandedUi) {
            if (selectedNavigationDestination == CampfireViewModel.NavigationDestination.SONGS) {
                SortingModesIconAndDropdown(
                    uiStrings = uiStrings,
                    stateHolder = stateHolder
                )
            }
            if (selectedNavigationDestination == CampfireViewModel.NavigationDestination.SONGS || selectedNavigationDestination == CampfireViewModel.NavigationDestination.SETLISTS) {
                FiltersIconAndDropdown(
                    uiStrings = uiStrings,
                    stateHolder = stateHolder
                )
            }
        }
    },
    backgroundColor = MaterialTheme.colors.background,
    title = {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            when (selectedNavigationDestination) {
                CampfireViewModel.NavigationDestination.SONGS -> {
                    SearchItem(
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        uiStrings = uiStrings,
                        query = query,
                        onQueryChanged = onQueryChanged
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                CampfireViewModel.NavigationDestination.SETLISTS -> Text(text = uiStrings.setlists)
                CampfireViewModel.NavigationDestination.SETTINGS -> Text(text = uiStrings.settings)
                else -> Unit
            }
        }
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

@Composable
private fun SortingModesIconAndDropdown(
    uiStrings: CampfireStrings,
    stateHolder: CampfireViewModelStateHolder
) {
    val isSortingDropdownVisible = remember { mutableStateOf(false) }

    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        IconButton(
            onClick = { isSortingDropdownVisible.value = !isSortingDropdownVisible.value }
        ) {
            Icon(
                imageVector = CampfireIcons.sort,
                contentDescription = uiStrings.songsSortingMode
            )
        }
        DropdownMenu(
            modifier = Modifier.widthIn(min = 200.dp, max = 300.dp),
            expanded = isSortingDropdownVisible.value,
            onDismissRequest = { isSortingDropdownVisible.value = false }
        ) {
            SongsSortingControlsList(
                modifier = Modifier.fillMaxSize(),
                uiStrings = stateHolder.uiStrings.value,
                sortingMode = stateHolder.userPreferences.value?.sortingMode,
                onSortingModeChanged = stateHolder::onSortingModeChanged
            )
        }
    }
}

@Composable
private fun FiltersIconAndDropdown(
    uiStrings: CampfireStrings,
    stateHolder: CampfireViewModelStateHolder
) {
    val isFilterDropdownVisible = remember { mutableStateOf(false) }

    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        IconButton(
            onClick = { isFilterDropdownVisible.value = !isFilterDropdownVisible.value }
        ) {
            Icon(
                imageVector = CampfireIcons.filter,
                contentDescription = uiStrings.songsFilters
            )
        }
        DropdownMenu(
            modifier = Modifier.widthIn(min = 200.dp, max = 300.dp),
            expanded = isFilterDropdownVisible.value,
            onDismissRequest = { isFilterDropdownVisible.value = false }
        ) {
            FilterControlsList(
                modifier = Modifier.fillMaxSize(),
                uiStrings = stateHolder.uiStrings.value,
                databases = stateHolder.databases.value,
                unselectedDatabaseUrls = stateHolder.userPreferences.value?.unselectedDatabaseUrls.orEmpty(),
                shouldShowExplicitSongs = stateHolder.userPreferences.value?.shouldShowExplicitSongs == true,
                shouldShowSongsWithoutChords = stateHolder.userPreferences.value?.shouldShowSongsWithoutChords == true,
                showOnlyDownloadedSongs = stateHolder.userPreferences.value?.showOnlyDownloadedSongs == true,
                onDatabaseSelectedChanged = stateHolder::onDatabaseSelectedChanged,
                onShouldShowExplicitSongsChanged = stateHolder::onShouldShowExplicitSongsChanged,
                onShouldShowSongsWithoutChordsChanged = stateHolder::onShouldShowSongsWithoutChordsChanged,
                onShowOnlyDownloadedSongsChanged = stateHolder::onShowOnlyDownloadedSongsChanged
            )
        }
    }
}

@Composable
private fun DynamicDialog(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    uiStrings: CampfireStrings,
    urlOpener: (String) -> Unit
) = stateHolder.visibleDialog.value.let { visibleDialog ->
    if (visibleDialog != null) {
        val firstTextInputValue = remember { mutableStateOf("") }
        val secondTextInputValue = remember { mutableStateOf("") }
        AlertDialog(
            modifier = modifier.defaultMinSize(minWidth = 300.dp),
            onDismissRequest = stateHolder::dismissDialog,
            title = {
                Text(
                    text = when (visibleDialog) {
                        CampfireViewModel.DialogType.NewSetlist -> uiStrings.setlistsNewSetlist
                        CampfireViewModel.DialogType.NewDatabase -> uiStrings.settingsAddNewDatabase
                        is CampfireViewModel.DialogType.SetlistPicker -> uiStrings.songDetailsAddToSetlist
                    }
                )
            },
            text = {
                Column {
                    Text(
                        modifier = Modifier.height(0.dp),
                        text = ""
                    )
                    when (visibleDialog) {
                        CampfireViewModel.DialogType.NewSetlist -> {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(uiStrings.setlistsNewSetlistTitle) },
                                value = firstTextInputValue.value,
                                onValueChange = { firstTextInputValue.value = it.replace("\n", "").take(40) },
                                singleLine = true
                            )
                        }

                        CampfireViewModel.DialogType.NewDatabase -> {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                label = { Text(uiStrings.settingsAddNewDatabaseName) },
                                value = firstTextInputValue.value,
                                onValueChange = { firstTextInputValue.value = it.replace("\n", "").take(30) },
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(uiStrings.settingsAddNewDatabaseUrl) },
                                value = secondTextInputValue.value,
                                onValueChange = { secondTextInputValue.value = it.replace("\n", "") },
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                modifier = Modifier.clickable { urlOpener("https://pandulapeter.github.io/campfire/documents/adding-new-databases.html") }, // TODO: Change URL
                                style = TextStyle.Default.copy(fontWeight = FontWeight.Bold),
                                text = uiStrings.settingsAddNewDatabaseHint
                            )
                        }

                        is CampfireViewModel.DialogType.SetlistPicker -> {
                            stateHolder.setlists.value.forEach { setlist ->
                                CheckboxItem(
                                    text = setlist.title,
                                    isEnabled = visibleDialog.currentSetlistId != setlist.id,
                                    isChecked = setlist.songIds.contains(visibleDialog.songId),
                                    onCheckedChanged = {
                                        if (it) {
                                            stateHolder.addSongToSetlist(
                                                songId = visibleDialog.songId,
                                                setlistId = setlist.id
                                            )
                                        } else {
                                            stateHolder.removeSongFromSetlist(
                                                songId = visibleDialog.songId,
                                                setlistId = setlist.id
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                when (visibleDialog) {
                    CampfireViewModel.DialogType.NewSetlist -> TextButton(
                        onClick = { stateHolder.createNewSetlist(firstTextInputValue.value) },
                        enabled = firstTextInputValue.value.isNotBlank()  // TODO: Improve validation
                    ) { Text(uiStrings.setlistsCreate) }

                    CampfireViewModel.DialogType.NewDatabase -> TextButton(
                        onClick = { stateHolder.createNewDatabase(firstTextInputValue.value, secondTextInputValue.value) },
                        enabled = firstTextInputValue.value.isNotBlank() && secondTextInputValue.value.isNotBlank()      // TODO: Improve validation
                    ) { Text(uiStrings.settingsAdd) }

                    is CampfireViewModel.DialogType.SetlistPicker -> Unit
                }
            }
        )
    }
}