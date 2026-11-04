/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.state.importing.LibraryItemToOpen

/**
 * Opens what an import turned out to be already in the library: a song on its details screen, a setlist on the setlists
 * screen, scrolled to. The setlists screen is selected the way its navigation item selects it, unless it is already on top,
 * which then only scrolls; where the navigation refuses (unsaved editor text, the setlists switched off), nothing moves.
 */
internal fun CampfireViewModel.openLibraryItem(item: LibraryItemToOpen) {
    when (item) {
        is LibraryItemToOpen.Song -> navigator.openImportedSong(item.fileName)
        is LibraryItemToOpen.Setlist -> {
            val isOnTop = navigator.backStack.lastOrNull() == CampfireDestination.Setlists
            if (!isOnTop) navigator.selectTopLevelDestination(CampfireDestination.Setlists)
            if (navigator.backStack.lastOrNull() == CampfireDestination.Setlists) {
                setlistsController.revealSetlist(item.fileName, isAnimated = isOnTop)
            }
        }
    }
}
