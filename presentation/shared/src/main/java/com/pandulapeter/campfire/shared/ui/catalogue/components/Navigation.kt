package com.pandulapeter.campfire.shared.ui.catalogue.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireIcons
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import com.pandulapeter.campfire.shared.ui.catalogue.theme.CampfireColors
import com.pandulapeter.campfire.shared.ui.screenComponents.songs.SongsFilterControlsList
import com.pandulapeter.campfire.shared.ui.screenComponents.songs.SongsSortingControlsList
import dev.atsushieno.composempp.material.AlertDialog
import dev.atsushieno.composempp.material.DropdownMenu

@OptIn(ExperimentalMaterialApi::class, ExperimentalAnimationApi::class)
@Composable
fun CampfireScaffold(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    modalBottomSheetState: ModalBottomSheetState,
    rawSongDetails: Map<String, RawSongDetails>?,
    onSongClosed: () -> Unit,
    query: String,
    onQueryChanged: (String) -> Unit,
    navigationDestinations: List<CampfireViewModel.NavigationDestinationWrapper>,
    isInLandscape: Boolean,
    shouldUseExpandedUi: Boolean,
    stateHolder: CampfireViewModelStateHolder,
    appBarActions: @Composable RowScope.() -> Unit = {},
    bottomNavigationBar: @Composable () -> Unit,
    navigationRail: @Composable (scaffoldPadding: PaddingValues, content: @Composable () -> Unit) -> Unit,
    content: @Composable (scaffoldPadding: PaddingValues?) -> Unit,
    urlOpener: (String) -> Unit
) = ModalBottomSheetLayout(
    modifier = modifier,
    sheetState = modalBottomSheetState,
    sheetElevation = 0.dp,
    scrimColor = Color.Transparent,
    sheetContent = {
        SongDetailsScreen(
            uiStrings = uiStrings,
            stateHolder = stateHolder,
            song = stateHolder.selectedSong.value,
            rawSongDetails = stateHolder.selectedSong.value?.let { rawSongDetails?.get(it.url) },
            setlists = stateHolder.setlists.value,
            onSongClosed = onSongClosed
        )
    }
) {
    val selectedNavigationDestination = navigationDestinations.firstOrNull { it.isSelected }?.destination
    Scaffold(
        scaffoldState = stateHolder.scaffoldState,
        topBar = {
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
                                modifier = Modifier.width(168.dp).padding(vertical = 8.dp),
                                uiStrings = uiStrings,
                                query = query,
                                onQueryChanged = onQueryChanged
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (!shouldUseExpandedUi) {
                                SortingModesIconAndDropdown(
                                    uiStrings = uiStrings,
                                    stateHolder = stateHolder
                                )
                                FiltersIconAndDropdown(
                                    uiStrings = uiStrings,
                                    stateHolder = stateHolder
                                )
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
            if (isInLandscape) {
                navigationRail(scaffoldPadding) { content(null) }
            } else {
                content(scaffoldPadding)
            }
        }
    )
    DynamicDialog(
        stateHolder = stateHolder,
        uiStrings = uiStrings,
        urlOpener = urlOpener
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
            SongsFilterControlsList(
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
                                onValueChange = { firstTextInputValue.value = it },
                                singleLine = true
                            )
                        }
                        CampfireViewModel.DialogType.NewDatabase -> {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                label = { Text(uiStrings.settingsAddNewDatabaseName) },
                                value = firstTextInputValue.value,
                                onValueChange = { firstTextInputValue.value = it },
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(uiStrings.settingsAddNewDatabaseUrl) },
                                value = secondTextInputValue.value,
                                onValueChange = { secondTextInputValue.value = it },
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                modifier = Modifier.clickable { urlOpener("https://github.com/pandulapeter") }, // TODO: Change URL
                                style = TextStyle.Default.copy(fontWeight = FontWeight.Bold),
                                text = uiStrings.settingsAddNewDatabaseHint
                            )
                        }
                        is CampfireViewModel.DialogType.SetlistPicker -> {
                            stateHolder.setlists.value.forEach { setlist ->
                                CheckboxItem(
                                    text = setlist.title,
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
                TextButton(
                    onClick = {
                        when (visibleDialog) {
                            CampfireViewModel.DialogType.NewSetlist -> stateHolder.createNewSetlist(firstTextInputValue.value)
                            CampfireViewModel.DialogType.NewDatabase -> stateHolder.createNewDatabase(firstTextInputValue.value, secondTextInputValue.value)
                            is CampfireViewModel.DialogType.SetlistPicker -> stateHolder.dismissDialog()
                        }
                    },
                    // TODO: Improve validation
                    enabled = when (visibleDialog) {
                        CampfireViewModel.DialogType.NewSetlist -> firstTextInputValue.value.isNotBlank()
                        CampfireViewModel.DialogType.NewDatabase -> firstTextInputValue.value.isNotBlank() && secondTextInputValue.value.isNotBlank()
                        is CampfireViewModel.DialogType.SetlistPicker -> true
                    }
                ) {
                    Text(
                        when (visibleDialog) {
                            CampfireViewModel.DialogType.NewSetlist -> uiStrings.setlistsCreate
                            CampfireViewModel.DialogType.NewDatabase -> uiStrings.settingsAdd
                            is CampfireViewModel.DialogType.SetlistPicker -> uiStrings.songDetailsDone
                        }
                    )
                }
            }
        )
    }
}