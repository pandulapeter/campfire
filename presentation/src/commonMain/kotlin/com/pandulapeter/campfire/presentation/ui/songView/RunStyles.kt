/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.ui.text.TextStyle

/**
 * The styles a run of tablature or a grid is drawn in: the lyrics' and the chords' own, which engraved tablature is set
 * in, and the same in the monospaced face, for what only lines up character by character (preformatted text, grids).
 */
internal data class RunStyles(
    val text: TextStyle,
    val chord: TextStyle,
    val monospaceText: TextStyle,
    val monospaceChord: TextStyle,
)
