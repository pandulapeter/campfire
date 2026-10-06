/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.model

/**
 * A shape a song gives one of its chords, from a `{define}` or a `{chord}` directive: how that chord is played in this
 * song, on [instrument], whatever the player's habit or the app's own first shape. A `{chord}`, which the specification
 * has show a diagram only where it stands, gives one only where the song has no `{define}` of that chord.
 *
 * @property name The chord the shape is for, as written, in the standard notation like the rest of the model.
 * @property movedBy How many frets a transposition moved the shape by, zero for one that is as the file writes it.
 */
data class ChordDefinition(
    val name: String,
    val instrument: ChordInstrument,
    val voicing: ChordVoicing,
    val movedBy: Int = 0,
)
