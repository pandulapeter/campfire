/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * The metronome's preferences: how a click sounds wherever it is played, and the Metronome tab's own tempo and time
 * signature, which a song's click never touches. The choices are stored as the metronome's own ids rather than as its
 * types, so that the model depends on nothing; an id this version does not know reads as the default.
 *
 * @param volume From 0 to 1, on top of the system's own volume; at 0 the click runs on with nothing sounding, for the
 *   visual beat alone.
 * @param beatLevels The accents the user drew for a time signature, keyed by the signature as written ("7/8"), so
 *   that a song in 7/8 is clicked 2+2+3 once that was set for 7/8 anywhere. A list whose length is not the
 *   signature's number of beats is ignored.
 * @param bpm The Metronome tab's own tempo.
 * @param timeSignature The Metronome tab's own, as written ("4/4").
 * @param isSongPanelShown Whether the song details screen opens with the metronome panel in its app bar: a player who
 *   reads every song to a click wants it there for the next song too, so it is a preference rather than something each
 *   screen asks for again.
 */
data class MetronomeSettings(
    val soundId: String = "click",
    val volume: Float = 1f,
    val subdivisionId: String = "none",
    val isVisualBeatEnabled: Boolean = true,
    val isHapticBeatEnabled: Boolean = false,
    val beatLevels: Map<String, List<String>> = emptyMap(),
    val bpm: Int = 120,
    val timeSignature: String = "4/4",
    val isSongPanelShown: Boolean = false,
) {

    companion object {
        /** The tempos the metronome plays, and so the only ones a stored override may hold. */
        val TEMPO_RANGE = 30..300
    }
}
