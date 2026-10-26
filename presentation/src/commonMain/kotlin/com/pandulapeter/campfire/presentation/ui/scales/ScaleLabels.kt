/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.scales

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.chordpro.scales.ScaleSpelling
import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/** What the scale sheet writes in the dots of its neck, flipped in its header and kept on the device. */
internal enum class ScaleLabels {
    DEGREES,
    NOTES,
}

/** The labels these preferences pick for the scale sheet's neck, degrees until they are read. */
internal val UserPreferences?.scaleLabels get() = if (this?.areScaleNoteNamesShown == true) ScaleLabels.NOTES else ScaleLabels.DEGREES

/** A degree label of the catalogue (`b3`, `#4`, `bb7`) with the accidental signs a reader sees (`♭3`, `♯4`, `♭♭7`). */
internal fun displayedDegree(degree: String) = degree.replace('b', '♭').replace('#', '♯')

/**
 * The notes of [scale] played on [rootName] as a screen draws them in [notation]: `ScaleSpelling.noteNames` with its
 * accidentals as `♯` and `♭` (`F♯`, `Si♭`), the way `shownChordName` draws a chord.
 */
internal fun shownScaleNoteNames(scale: Scale, rootName: String, notation: ChordNotation) =
    ScaleSpelling.noteNames(scale, rootName, notation).map(ChordProNotation::withAccidentalSigns)
