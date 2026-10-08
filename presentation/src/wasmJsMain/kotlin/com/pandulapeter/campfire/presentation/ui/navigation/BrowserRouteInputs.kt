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

import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * What [BrowserRoutes.paths] reads, as the view model holds it now. Read inside a `snapshotFlow`, it observes the
 * snapshot states the addresses depend on; the searches and the dialog are flows, which the caller combines in.
 */
internal fun CampfireViewModel.routeInputs() = RouteInputs(
    backStack = backStack.toList(),
    isSongsSearchOpen = songsSearch.isOpen.value,
    isSetlistsSearchOpen = setlistsSearch.isOpen.value,
    isSetlistReordering = isSetlistReordering,
    settingsTab = settingsTab,
    currentSongFileNames = backStack.filterIsInstance<CampfireDestination.SongDetails>().associate { it.id to currentSongFileName(it) },
    hasOverlay = RouteInputs.hasOverlay(dialog = visibleDialog.value, isAnyMenuOpen = overlayState.isAnyMenuOpen),
)
