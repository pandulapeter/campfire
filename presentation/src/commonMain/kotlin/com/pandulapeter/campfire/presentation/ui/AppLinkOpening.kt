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
import com.pandulapeter.campfire.presentation.ui.launch.LinkRefusal
import com.pandulapeter.campfire.presentation.ui.navigation.AppLinks
import com.pandulapeter.campfire.presentation.ui.navigation.BrowserRoutes
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.ClipboardTextReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Opens what an app link names ([AppLinks]), a launcher shortcut's or a home screen widget's among them: the place its
 * web address opens ([BrowserRoutes.resolve]), else the songs or the setlists screen for a song or a setlist the library
 * no longer holds, else the home screen ([BrowserRoutes.fallbackPathOf]). A setlist's own address brings it into sight
 * on the setlists screen ([BrowserRoutes.setlistToRevealOf]). New song and Paste a song
 * ([BrowserRoutes.LinkAction]) then do what the songs screen's New button and Paste from clipboard do, once the app is on
 * screen, and only where those are offered; a paste reads the clipboard with [clipboard], which asks the system as the
 * button does. Over what leaving would lose nothing happens ([LinkRefusal]). A dialog open stays, since it may hold
 * typed text, and New song and Paste a song are then not offered.
 */
internal fun CampfireViewModel.openAppLink(link: String, clipboard: ClipboardTextReader) {
    val path = AppLinks.pathOf(link) ?: return
    val action = BrowserRoutes.LinkAction.of(path)
    // The first segment alone, which names a screen: the rest may name a song.
    println("App link opened: ${action?.name ?: path.substringBefore('/').ifEmpty { "home" }}")
    viewModelScope.launch {
        var revealedSetlist: String? = null
        val refusal = launchState.navigateToLink { songs, setlists, isPerformanceModeEnabled ->
            val current = navigator.navigationState
            revealedSetlist = BrowserRoutes.setlistToRevealOf(path = path, setlists = setlists)
            BrowserRoutes.resolve(path = path, songs = songs, setlists = setlists, current = current, isPerformanceModeEnabled = isPerformanceModeEnabled)
                ?: run {
                    println("App link names nothing in the library")
                    val fallback = BrowserRoutes.fallbackPathOf(path)
                    BrowserRoutes.resolve(path = fallback, songs = songs, setlists = setlists, current = current, isPerformanceModeEnabled = isPerformanceModeEnabled)
                }
        }
        if (refusal != null) return@launch println("App link not followed: ${refusal.name.lowercase()}")
        revealedSetlist?.let { fileName ->
            if (navigator.backStack.lastOrNull() == CampfireDestination.Setlists) setlistsController.revealSetlist(fileName, isAnimated = false)
        }
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
