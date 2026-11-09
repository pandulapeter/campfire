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

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll.SongAutoScrolling
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.PageStepper
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What a step does, whichever presses it (the step buttons, their keys, a tap on either half of the song): through the
 * song being read ([stepper], or a nudge where it scrolls by itself, [continuousScrolling]), and at either end of it on
 * to the song beside it. Built afresh as the screen recomposes, since it is read at the time of the press.
 *
 * @param pageScrollStates The scroll of every page that is composed, see [stepBack].
 * @param continuousScrolling Null where the song is read in pages.
 * @param lastPage The index of the last song the pager has.
 */
internal class SongStepActions(
    private val coroutineScope: CoroutineScope,
    private val pagerState: PagerState,
    private val pageStepper: PageStepper,
    private val pageScrollStates: Map<Int, ScrollState>,
    private val stepper: SongStepper?,
    private val continuousScrolling: SongAutoScrolling?,
    private val lastPage: Int,
    private val nextSongConfirmation: NextSongConfirmation,
) {
    /** Whether the song is a strip read from the right, null where it is no strip, see [SongStepper.readsLeftwards]. */
    val stripReadsLeftwards get() = stepper?.takeIf { it.isAcross }?.readsLeftwards

    /** A step back, from where a gesture the step stands in for began ([from]) or from where the song is. */
    fun stepBack(from: Int?) {
        nextSongConfirmation.dismiss()
        if (continuousScrolling?.nudge(-1) == true) return
        if (continuousScrolling == null && stepper?.canStep(-1, from) == true) {
            coroutineScope.launch { stepper.step(-1, from) }
        } else if (pagerState.targetPage > 0) {
            // Going back from the top of a song is going back to the last lines of the one before it, and landing on its
            // top instead would have the next press skip that song whole. A page not laid out yet has nowhere to go but
            // its top.
            pageScrollStates[pagerState.targetPage - 1]?.let { state -> coroutineScope.launch { state.scrollTo(state.maxValue) } }
            pageStepper.step(-1)
        }
    }

    /** A step forward, the way [stepBack] is, leaving the end of the song only once [NextSongConfirmation] lets it. */
    fun stepForward(from: Int?) {
        if (continuousScrolling?.nudge(1) == true) return
        if (continuousScrolling == null && stepper?.canStep(1, from) == true) {
            coroutineScope.launch { stepper.step(1, from) }
        } else if (pagerState.targetPage < lastPage && nextSongConfirmation.confirm(pagerState.targetPage)) {
            pageStepper.step(1)
        }
    }
}
