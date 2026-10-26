/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.pandulapeter.campfire.presentation.ui.components.menu.LocalOverlayState
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists_create_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_search
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.components.appBar.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.list.ListUnderAppBar
import com.pandulapeter.campfire.presentation.ui.components.menu.NewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.Placeholder
import com.pandulapeter.campfire.presentation.ui.components.list.ListLayout
import com.pandulapeter.campfire.presentation.ui.components.appBar.SearchableTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.menu.SetlistSortMenu
import com.pandulapeter.campfire.presentation.ui.components.list.allowsNewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.appBar.animateAppBarReveal
import com.pandulapeter.campfire.presentation.ui.components.menu.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.list.animateScrollToTop
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedLazyGridState
import com.pandulapeter.campfire.presentation.ui.components.appBar.underAppBar
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun SetlistsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: ListLayout,
    contentPadding: PaddingValues,
) {
    val listState = rememberRetainedLazyGridState(viewModel.setlistsController.setlistsScrollPosition)
    val isPerformanceModeEnabled by viewModel.preferencesController.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val setlistsPlaceholder by viewModel.setlistsController.setlistsPlaceholder.collectAsStateWithLifecycle()
    val listedSetlists by viewModel.setlistsController.setlistsWithSongs.collectAsStateWithLifecycle()
    // The read rather than the placeholder: an import into a library with no setlists shows the loading placeholder
    // too, and the bar keyed on that would be built afresh as the import ends, an open search field with it.
    val isAwaitingSetlists = setlistsPlaceholder == Placeholder.LOADING && !listedSetlists.isLibraryRead
    val visibleDialog by viewModel.dialogHost.visibleDialog.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    // Not saveable: returning to this screen always starts in browsing mode.
    val reorderingSetlistFileName = viewModel.setlistsController.reorderingSetlistFileName
    val isScreenActive = viewModel.navigator.backStack.lastOrNull() == CampfireDestination.Setlists
    val isReordering = reorderingSetlistFileName != null && isScreenActive && !isPerformanceModeEnabled
    val isSearchOpen by viewModel.setlistsController.setlistsSearch.isOpen.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) viewModel.setlistsController.reorderingSetlistFileName = null
    }
    LaunchedEffect(userPreferences?.setlistSortingMode) {
        viewModel.setlistsController.reorderingSetlistFileName = null
    }
    val columnCount = layout.setlistColumnCount
    HideKeyboardWhenScrolledDown(listState, isEnabled = visibleDialog == null)
    LaunchedEffect(viewModel, listState) {
        // Latest, for the reason the settings screen gives.
        viewModel.navigator.scrollToTopRequests.collectLatest { if (it.destination == CampfireDestination.Setlists) listState.animateScrollToTop() }
    }
    // See the songs screen: how far the app bar's buttons reach in over a pinned header, which it narrows to clear.
    var appBarReach by remember { mutableStateOf(0.dp) }
    val appBarReveal = animateAppBarReveal(
        searchState = viewModel.setlistsController.setlistsSearch,
        isShownWithoutSearch = setlistsPlaceholder != null,
    )
    // Built lazily and read only while the lists lay out, so that the bar's spring moves the headers and the scroller
    // without recomposing the list on every one of its frames.
    val appBarOverlap: () -> AppBarOverlap = remember(appBarReveal) {
        { AppBarOverlap.of(reach = appBarReach, appBarReveal = appBarReveal.value) }
    }
    ListUnderAppBar(
        modifier = modifier.fillMaxSize(),
        list = {
            SetlistList(
                modifier = Modifier.fillMaxSize().underAppBar { appBarReveal.value },
                viewModel = viewModel,
                listState = listState,
                columnCount = columnCount,
                contentPadding = contentPadding,
                appBarOverlap = appBarOverlap,
                reorderingSetlistFileName = reorderingSetlistFileName,
                isReordering = isReordering,
                isScreenActive = isScreenActive,
                onReorderingSetlistChanged = {
                    viewModel.setlistsController.reorderingSetlistFileName = it
                    if (it != null) {
                        keyboardController?.hide()
                        viewModel.setlistsController.setlistsSearch.close()
                    }
                },
            )
        },
    ) {
        // Built afresh once the setlists first arrive, for the reason the songs screen gives.
        key(isAwaitingSetlists) {
            SearchableTopAppBar(
                contentPadding = contentPadding,
                appBarReveal = { appBarReveal.value },
                placeholder = stringResource(Res.string.setlists_search),
                searchState = viewModel.setlistsController.setlistsSearch,
                isSearchEnabled = !isReordering,
                onReachChanged = { appBarReach = it },
                areClosedSearchActionsShown = !isPerformanceModeEnabled && setlistsPlaceholder.allowsNewItemMenu,
                areClosedSearchActionsDetached = true,
                closedSearchActions = {
                    NewItemMenu(
                        contentDescription = stringResource(Res.string.setlists_new_setlist),
                        createLabel = stringResource(Res.string.setlists_create_setlist),
                        onCreate = { viewModel.dialogHost.showDialog(DialogType.NewSetlist) },
                        onImport = viewModel.importController::importFiles,
                        onItemSelected = { viewModel.setlistsController.reorderingSetlistFileName = null },
                        isEnabled = !isReordering,
                    )
                },
                actions = {
                    SetlistSortMenu(
                        modifier = Modifier.overlappingAction(),
                        selected = userPreferences?.setlistSortingMode,
                        onSelected = viewModel.setlistsController::setSetlistSortingMode,
                        onSortingModeChanged = { viewModel.setlistsController.reorderingSetlistFileName = null },
                        isEnabled = !isReordering,
                    )
                },
            )
        }
    }
    val backGesture = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = backGesture,
        isBackEnabled = isReordering && visibleDialog == null && !LocalOverlayState.current.isAnyMenuOpen,
        onBackCompleted = { viewModel.setlistsController.reorderingSetlistFileName = null },
    )
}
