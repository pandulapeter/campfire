/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import com.pandulapeter.campfire.metronome.api.model.MetronomeOrigin
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/**
 * What a click plays for right now: the song on the topmost song details screen of the back stack, wherever that is
 * in the stack - the editor pushed over a song keeps the click in the song's tempo - or nothing in particular, which
 * is the Metronome tab's own pattern.
 */
internal sealed interface MetronomeContext {

    data object Standalone : MetronomeContext

    data class Song(
        val songFileName: String,
        val setlistFileName: String?,
    ) : MetronomeContext

    val origin
        get() = when (this) {
            Standalone -> MetronomeOrigin.Standalone
            is Song -> MetronomeOrigin.Song(songFileName = songFileName, setlistFileName = setlistFileName)
        }
}

/**
 * The context of [backStack]: the topmost song details screen's current song, as [currentSongOf] answers it (the page
 * its pager is heading for), or [MetronomeContext.Standalone] where there is none.
 */
internal fun metronomeContextOf(
    backStack: List<CampfireDestination>,
    currentSongOf: (CampfireDestination.SongDetails) -> String?,
): MetronomeContext {
    val destination = backStack.lastOrNull { it is CampfireDestination.SongDetails } as? CampfireDestination.SongDetails
        ?: return MetronomeContext.Standalone
    val songFileName = currentSongOf(destination) ?: return MetronomeContext.Standalone
    return MetronomeContext.Song(songFileName = songFileName, setlistFileName = destination.setlistFileName)
}

/** What a playing click does when the back stack moves it from [previous] to [current]. */
internal sealed interface MetronomeRetarget {

    /** Nothing: the context did not change, or nothing plays. */
    data object None : MetronomeRetarget

    /** Plays the new context's pattern from beat one. */
    data object Restart : MetronomeRetarget

    data object Stop : MetronomeRetarget
}

/**
 * Opening or paging to a song retargets the click to it; the song leaving the back stack takes the click back to
 * where it was started: a click started on a song stops, one started on the Metronome tab goes back to the tab's own
 * pattern and carries on.
 */
internal fun metronomeRetargetOf(previous: MetronomeContext, current: MetronomeContext, origin: MetronomeOrigin?) = when {
    origin == null || previous == current -> MetronomeRetarget.None
    current is MetronomeContext.Song -> MetronomeRetarget.Restart
    origin is MetronomeOrigin.Song -> MetronomeRetarget.Stop
    else -> MetronomeRetarget.Restart
}
