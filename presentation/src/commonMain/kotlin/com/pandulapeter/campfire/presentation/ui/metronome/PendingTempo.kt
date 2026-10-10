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

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback

/** The tempo a playing click moves to once the bar being heard has ended, and the one it is heard at until then. */
@Immutable
internal data class PendingTempo(
    val fromBpm: Int,
    val toBpm: Int,
)

/**
 * The [PendingTempo] of [playback], or null where nothing waits for the next bar or what waits keeps the tempo: a song
 * moved to at the tempo of the one before only starts its count of bars again, which nobody needs to be told about.
 */
internal fun pendingTempoOf(playback: MetronomePlayback): PendingTempo? {
    val playing = playback as? MetronomePlayback.Playing ?: return null
    val pending = playing.pendingPattern ?: return null
    return PendingTempo(fromBpm = playing.pattern.bpm, toBpm = pending.bpm).takeIf { it.fromBpm != it.toBpm }
}
