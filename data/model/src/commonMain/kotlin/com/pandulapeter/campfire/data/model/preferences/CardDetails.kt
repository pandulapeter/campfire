/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.preferences

/**
 * What a song card says after the artist, chosen for each list on its own: a setlist's cards are where a set is
 * timed, the library's where a song is found. The key is only ever shown with the chords switched on, whatever
 * [isKeyShown] says, so that what it is set to is kept for when they come back. The tempo is a song's whether it is
 * clicked or not, so it can be shown without the metronome, but it is what the click plays: [isTempoShown] is null
 * until the reader picks it, and is then the metronome switch ([isTempoShown] with an argument), so that switching
 * the metronome off takes the tempo off the cards without writing a thing.
 */
data class CardDetails(
    val isKeyShown: Boolean,
    val isTempoShown: Boolean?,
    val isDurationShown: Boolean,
) {

    /** Whether the tempo is on the cards, given the metronome switch, see [isTempoShown]. */
    fun isTempoShown(isMetronomeEnabled: Boolean) = isTempoShown ?: isMetronomeEnabled

    companion object {
        val SongsDefault = CardDetails(isKeyShown = true, isTempoShown = null, isDurationShown = false)
        val SetlistsDefault = CardDetails(isKeyShown = true, isTempoShown = null, isDurationShown = true)
    }
}
