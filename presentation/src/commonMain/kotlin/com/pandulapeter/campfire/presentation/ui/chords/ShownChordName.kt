/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation

/**
 * A chord or a note in the standard notation as a screen or a page draws it in [notation]: `ChordProNotation.shownName`
 * with its accidentals as `♯` and `♭`. Only for drawing; what is typed, compared, or written keeps `shownName`'s ASCII.
 */
internal fun shownChordName(name: String, notation: ChordNotation) = ChordProNotation.withAccidentalSigns(ChordProNotation.shownName(name, notation))
