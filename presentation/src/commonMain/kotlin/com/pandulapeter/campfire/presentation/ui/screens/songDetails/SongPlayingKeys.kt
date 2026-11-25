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
 * What the keys that set how the song is played need (Ctrl / Cmd + Shift + Up and Down, T), given only where the dock's
 * chips set it: not read only, where they only read.
 *
 * @param areChordsShown Whether the key chip transposes, which the song's own chords then decide.
 * @param isTempoShown Whether the tempo chip is there, which the metronome switch decides.
 */
internal class SongPlayingKeys(
    val overrides: PlayingOverrides,
    val areChordsShown: Boolean,
    val isTempoShown: Boolean,
)
