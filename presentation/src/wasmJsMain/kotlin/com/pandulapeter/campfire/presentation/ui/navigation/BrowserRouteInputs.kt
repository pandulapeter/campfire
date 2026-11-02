/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.state.setlists.SetlistsController
import com.pandulapeter.campfire.presentation.ui.state.songs.SongListState
import com.pandulapeter.campfire.presentation.ui.state.songs.SongSelection

/**
 * What [BrowserRoutes.paths] reads, as the app's holders hold it now. Read inside a `snapshotFlow`, it observes the
 * snapshot states the addresses depend on; the searches and the dialog are flows, which the caller combines in.
 */
internal fun routeInputsOf(
    navigator: Navigator,
    songListState: SongListState,
    setlistsController: SetlistsController,
    songSelection: SongSelection,
    dialogHost: DialogHost,
) = RouteInputs(
    backStack = navigator.backStack.toList(),
    isSongsSearchOpen = songListState.songsSearch.isOpen.value,
    isSetlistsSearchOpen = setlistsController.setlistsSearch.isOpen.value,
    isSetlistReordering = setlistsController.isSetlistReordering,
    isSongSelecting = songSelection.isSongSelecting,
    settingsTab = navigator.settingsTab,
    currentSongFileNames = navigator.backStack.filterIsInstance<CampfireDestination.SongDetails>().associate { it.id to navigator.currentSongFileName(it) },
    hasOverlay = RouteInputs.hasOverlay(dialog = dialogHost.visibleDialog.value, isAnyMenuOpen = dialogHost.overlayState.isAnyMenuOpen),
)
