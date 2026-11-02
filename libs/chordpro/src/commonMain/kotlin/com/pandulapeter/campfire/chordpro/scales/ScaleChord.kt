/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.scales

/**
 * One chord a scale's notes stack into, see [ScaleChords.triads].
 *
 * @property numeral The degree it stands on as a Roman numeral, its case and sign saying its quality (`ii`, `vii°`,
 *   `bIII+`), as [com.pandulapeter.campfire.chordpro.chords.ChordNotation.ROMAN] writes it.
 * @property name The chord's name in the standard notation (`Dm`, `Bm7b5`).
 */
public data class ScaleChord(
    val numeral: String,
    val name: String,
)
