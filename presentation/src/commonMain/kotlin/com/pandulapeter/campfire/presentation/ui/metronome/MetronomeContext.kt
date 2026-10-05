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

import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/**
 * What a click plays for right now: the song on the song details screen, which is one of the two screens that hold a
 * metronome, or nothing in particular, which is the Metronome tab's own pattern. Nothing else can be playing, since a
 * click stops with the screen it is played from.
 */
internal sealed interface MetronomeContext {

    data object Standalone : MetronomeContext

    data class Song(
        val songFileName: String,
        val setlistFileName: String?,
    ) : MetronomeContext
}

/**
 * The context of [backStack]: the song details screen on top of it, at the page its pager is heading for
 * ([currentSongOf]), or [MetronomeContext.Standalone] for every other screen - the Metronome tab, and the screens a
 * click never survives.
 */
internal fun metronomeContextOf(
    backStack: List<CampfireDestination>,
    currentSongOf: (CampfireDestination.SongDetails) -> String?,
): MetronomeContext {
    val destination = backStack.lastOrNull() as? CampfireDestination.SongDetails ?: return MetronomeContext.Standalone
    val songFileName = currentSongOf(destination) ?: return MetronomeContext.Standalone
    return MetronomeContext.Song(songFileName = songFileName, setlistFileName = destination.setlistFileName)
}

/**
 * Whether a back stack change leaves the screen a click is played from: the new top holds no metronome, or it is
 * another screen than the one that was on top - a song opened over the Metronome tab, or over another song.
 */
internal fun isMetronomeScreenLeft(previousTop: CampfireDestination?, top: CampfireDestination?) =
    (top !is CampfireDestination.SongDetails && top != CampfireDestination.Metronome) || top?.contentKey != previousTop?.contentKey

/** Whether [context] is another song than [last] rather than the same one under the name a rename gave it. */
internal fun isMetronomeContextMoved(last: MetronomeContext, context: MetronomeContext, renames: Map<String, String>) =
    context != last && !(
        last is MetronomeContext.Song && context is MetronomeContext.Song &&
            last.setlistFileName == context.setlistFileName && renames[last.songFileName] == context.songFileName
        )
