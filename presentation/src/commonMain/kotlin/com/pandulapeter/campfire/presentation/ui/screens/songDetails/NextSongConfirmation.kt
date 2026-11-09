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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The second press a step needs to leave the end of a song for the next one of a setlist. A player on stage stepping
 * with a pedal cannot see the end coming the way a reader scrolling by hand does, and one press too many would put the
 * next song on screen in the middle of the last chorus; so the first press past the end only says what comes next (the
 * pager bar, `SongPagerControls`), and the press after it, however much later, goes there. A pedal's repeated press is
 * the confirmation, so nothing else has to be reached for.
 *
 * Only the step leaving a song forward asks: the pager bar's buttons, swipes, and Left / Right name the song they go to.
 */
internal class NextSongConfirmation(
    private val isAsked: Boolean,
) {
    /** The page whose end the last press was stopped at, waiting for the press that leaves it. */
    var armedPage by mutableStateOf<Int?>(null)
        private set

    /**
     * Whether the step from the end of [page] to the next song may go ahead now: at once where nothing is asked,
     * otherwise only on the press after the one that stopped there.
     */
    fun confirm(page: Int) = when {
        !isAsked -> true
        armedPage == page -> {
            armedPage = null
            true
        }
        else -> {
            armedPage = page
            false
        }
    }

    /** Forgets the press waiting for its confirmation: the reader went back, or left the end of the song some other way. */
    fun dismiss() {
        armedPage = null
    }
}

/**
 * A [NextSongConfirmation] that forgets the press it waits on once the reader is no longer at the end of the page it
 * was made on ([targetPage], [canStepForwardInSong]): scrolled back into the song, or on another song, so returning
 * there later starts again from the first press.
 */
@Composable
internal fun rememberNextSongConfirmation(
    isAsked: Boolean,
    targetPage: Int,
    canStepForwardInSong: Boolean,
): NextSongConfirmation {
    val confirmation = remember(isAsked) { NextSongConfirmation(isAsked) }
    LaunchedEffect(confirmation, targetPage, canStepForwardInSong) {
        if (canStepForwardInSong || confirmation.armedPage != targetPage) confirmation.dismiss()
    }
    return confirmation
}
