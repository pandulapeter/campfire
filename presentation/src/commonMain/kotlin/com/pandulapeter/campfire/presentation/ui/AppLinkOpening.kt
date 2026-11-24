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

import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.AppLinks
import com.pandulapeter.campfire.presentation.ui.navigation.BrowserRoutes
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.ClipboardTextReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Opens what an app link names ([AppLinks]), a launcher shortcut's among them: the place its web address opens
 * ([BrowserRoutes.resolve]), else the home screen, as a typed address does on the web. New song and Paste a song
 * ([BrowserRoutes.LinkAction]) then do what the songs screen's New button and Paste from clipboard do, once the app is on
 * screen, and only where those are offered; a paste reads the clipboard with [clipboard], which asks the system as the
 * button does. Over the editor's unsaved text nothing happens, since only the editor's own ways out may take it away.
 */
internal fun CampfireViewModel.openAppLink(link: String, clipboard: ClipboardTextReader) {
    val path = AppLinks.pathOf(link) ?: return
    val action = BrowserRoutes.LinkAction.of(path)
    // The first segment alone, which names a screen: the rest may name a song.
    println("App link opened: ${action?.name ?: path.substringBefore('/').ifEmpty { "home" }}")
    viewModelScope.launch {
        val isOpened = launchState.navigateToLink { songs, setlists, isPerformanceModeEnabled ->
            val current = navigator.navigationState
            BrowserRoutes.resolve(path = path, songs = songs, setlists = setlists, current = current, isPerformanceModeEnabled = isPerformanceModeEnabled)
                ?: BrowserRoutes.resolve(path = "", songs = songs, setlists = setlists, current = current, isPerformanceModeEnabled = isPerformanceModeEnabled)
        }
        if (!isOpened) return@launch println("App link not followed: unsaved editor text")
        if (action == null) return@launch
        launchState.isAppOnScreen.first { it }
        when (action) {
            BrowserRoutes.LinkAction.NEW_SONG -> {
                val isOffered = navigator.backStack.lastOrNull() == CampfireDestination.Songs && dialogHost.visibleDialog.value == null &&
                    !preferencesController.isPerformanceModeEnabled.value && !songSelection.isSongSelecting
                if (isOffered) dialogHost.showDialog(DialogType.NewSong) else println("App link's new song not offered here")
            }

            BrowserRoutes.LinkAction.PASTE_SONG -> if (pastedSongController.isNewSongPasteAvailable) {
                pastedSongController.newSongFromClipboard(clipboard)
            } else {
                println("App link's paste not offered here")
            }
        }
    }
}
