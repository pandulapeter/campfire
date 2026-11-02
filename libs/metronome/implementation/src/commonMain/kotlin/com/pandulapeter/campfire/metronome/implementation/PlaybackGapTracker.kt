/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome.implementation

import kotlin.concurrent.Volatile

/**
 * The frames a player ran with nothing queued in one session. The player's time runs on while nothing is queued, and a
 * buffer scheduled with no time of its own starts at once rather than at its frame of the stream: a queue that ran dry
 * adds its gap to the player's time for the rest of the session, which [heardFrame] takes back out.
 *
 * An object of each session's own rather than a field reset by start: the previous session's feed thread can be between
 * its check and its write while stop and the next start run, and then adds its gap only to a count nobody reads any more.
 */
internal class PlaybackGapTracker {

    @Volatile
    var silentFrames: Long = 0L
        private set

    /** Called before each buffer is scheduled, with the frames scheduled so far and the player's time, where it is known. */
    fun onBufferFreed(scheduledFrames: Long, playerFrame: Long?) {
        val expected = scheduledFrames + silentFrames
        if (playerFrame != null && playerFrame > expected) silentFrames += playerFrame - expected
    }

    /** The frame of the stream being heard when the player is at [playerFrame], [latencyFrames] still ahead of the ear. */
    fun heardFrame(playerFrame: Long, latencyFrames: Long): Long = playerFrame - silentFrames - latencyFrames
}
