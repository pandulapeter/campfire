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

import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverrides

/**
 * What the keys that press the song's playing controls need (Ctrl / Cmd + Shift + Up and Down, T), given only where the
 * song has those controls: not read only, where its four values are a line of text.
 *
 * @param areChordsShown Whether the transposition stepper can be there, which the song's own chords then decide.
 * @param isTempoShown Whether the tempo stepper and its Tap segment are, which the metronome switch decides.
 */
internal class SongPlayingKeys(
    val overrides: PlayingOverrides,
    val areChordsShown: Boolean,
    val isTempoShown: Boolean,
)
