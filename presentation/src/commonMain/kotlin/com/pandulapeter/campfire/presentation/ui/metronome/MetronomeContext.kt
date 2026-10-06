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

import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/**
 * What a click plays for right now: the song on the song details screen, which is one of the two screens that hold a
 * metronome, or nothing in particular, which is the Metronome tab's own pattern. Nothing else can be playing, since a
 * click stops with the screen it is played from.
 */
internal sealed interface MetronomeContext {

    data object Standalone : MetronomeContext

    /** @param timing Where in the song the page being read is, null at its opening. */
    data class Song(
        val songFileName: String,
        val setlistFileName: String?,
        val timing: SongTiming? = null,
    ) : MetronomeContext
}

/**
 * The stretch of a song after its [index]-th change of tempo or time signature (counted from 0), and how the page plays
 * it: the [bpm] it shows, an override of the song's opening tempo already scaled into it, null where the song names no
 * tempo at all, and the [timeSignature] it counts.
 */
internal data class SongTiming(
    val index: Int,
    val bpm: Int?,
    val timeSignature: TimeSignature,
)

/**
 * The context of [backStack]: the song details screen on top of it, at the page its pager is heading for
 * ([currentSongOf]), or [MetronomeContext.Standalone] for every other screen - the Metronome tab, and the screens a
 * click never survives.
 */
internal fun metronomeContextOf(
    backStack: List<CampfireDestination>,
    currentSongOf: (CampfireDestination.SongDetails) -> String?,
    currentTimingOf: (CampfireDestination.SongDetails) -> SongTiming? = { null },
): MetronomeContext {
    val destination = backStack.lastOrNull() as? CampfireDestination.SongDetails ?: return MetronomeContext.Standalone
    val songFileName = currentSongOf(destination) ?: return MetronomeContext.Standalone
    return MetronomeContext.Song(songFileName = songFileName, setlistFileName = destination.setlistFileName, timing = currentTimingOf(destination))
}

/**
 * Whether a back stack change leaves the screen a click is played from: the new top holds no metronome, or it is
 * another screen than the one that was on top - a song opened over the Metronome tab, or over another song.
 */
internal fun isMetronomeScreenLeft(previousTop: CampfireDestination?, top: CampfireDestination?) =
    (top !is CampfireDestination.SongDetails && top != CampfireDestination.Metronome) || top.contentKey != previousTop?.contentKey

/**
 * Whether [context] is another song than [last] rather than the same one under the name a rename gave it, or another
 * stretch of the same song. A stretch whose tempo changed with the song's opening one (a stepper, a tap) is the same
 * stretch, and the change is played from the next beat.
 */
internal fun isMetronomeContextMoved(last: MetronomeContext, context: MetronomeContext, renames: Map<String, String>) =
    context.place != last.place && !(
        last is MetronomeContext.Song && context is MetronomeContext.Song &&
            last.setlistFileName == context.setlistFileName && renames[last.songFileName] == context.songFileName
        )

/** Where in the app a click plays, which is the context without the values a stretch of a song is played with. */
private val MetronomeContext.place: Any
    get() = if (this is MetronomeContext.Song) copy(timing = timing?.let { SongTiming(index = it.index, bpm = null, timeSignature = TimeSignature.COMMON_TIME) }) else this
