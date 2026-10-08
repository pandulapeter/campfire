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

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType

/**
 * Everything [BrowserRoutes.paths] reads, so that the addresses are a function of plain values.
 *
 * @param currentSongFileNames The file name of the song each details screen of [backStack] is showing, by
 *   [CampfireDestination.SongDetails.id]: where it was opened until it is paged.
 * @param hasOverlay Whether a dialog, a sheet or a menu is open over the screen on top - the unsaved changes question
 *   aside, see [hasOverlay].
 */
internal data class RouteInputs(
    val backStack: List<CampfireDestination>,
    val isSongsSearchOpen: Boolean,
    val isSetlistsSearchOpen: Boolean,
    val isSetlistReordering: Boolean,
    val settingsTab: SettingsTab,
    val currentSongFileNames: Map<String, String?>,
    val hasOverlay: Boolean,
) {
    companion object {

        /** Whether [dialog] or an open menu gets a history entry of its own, see [BrowserRoutes.paths]. */
        fun hasOverlay(dialog: DialogType?, isAnyMenuOpen: Boolean) = dialog != null && dialog != DialogType.UnsavedChanges || isAnyMenuOpen
    }
}
