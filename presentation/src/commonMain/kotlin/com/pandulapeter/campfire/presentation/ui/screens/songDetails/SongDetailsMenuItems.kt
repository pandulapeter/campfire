/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.settings_preferences
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** Scrolls the song read in [scrollState] back to its top, where it is anywhere else; [source] names what asked, in the log. */
internal fun CoroutineScope.scrollBackToTop(scrollState: ScrollState?, source: String) {
    if (scrollState == null || scrollState.value == 0) return
    println("Back to the top: $source")
    launch { scrollState.animateScrollTo(0) }
}

/**
 * The About the song sheet, also reached by the song's title in the bar: the menu is where it is found from
 * anywhere in the song, and by somebody who never thought to press a title. Never let out of the menu, as a button would
 * only repeat the title.
 */
@Composable
internal fun aboutSongAction(onClick: () -> Unit) = ActionsMenuItem(
    title = stringResource(Res.string.song_details_song_info),
    icon = painterResource(Res.drawable.ic_info),
    isAlwaysInMenu = true,
    onClick = onClick,
)

/**
 * The Preferences sheet, never let out of the menu: it is changed now and then, not while a song is played. Read only
 * mode's mark opens it too, being the way out of the mode.
 */
@Composable
internal fun preferencesAction(onClick: () -> Unit) = ActionsMenuItem(
    title = stringResource(Res.string.settings_preferences),
    icon = painterResource(Res.drawable.ic_tune),
    isAlwaysInMenu = true,
    onClick = onClick,
)

/**
 * The song's own entries at the head of song details' menu, in the one order both menus (read only mode's too) keep:
 * Choose setlists ([setlistAssignments], where the bar has no room for its star), Preferences, the tuner ([tuner], where
 * the dock's card or the Tuner feature does not leave it out), then About the song ([aboutSong], never in a preview):
 * the tuner follows Preferences, both being how the player sets up to play, and About the song, read rather than set,
 * comes after them.
 */
internal fun <T : Any> songMenuItems(setlistAssignments: T?, preferences: T, tuner: T?, aboutSong: T?) =
    listOfNotNull(setlistAssignments, preferences, tuner, aboutSong)
