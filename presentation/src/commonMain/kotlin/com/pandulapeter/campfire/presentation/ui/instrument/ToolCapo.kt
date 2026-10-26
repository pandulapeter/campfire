/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.playing.playsWithoutCapo

/**
 * The fret the Chords tab's shapes are fingered above ([UserPreferences.toolCapo]), or null where the tab draws nothing
 * a capo could change and shows no capo stepper: the keyboard, which takes no capo (`playsWithoutCapo`), and an
 * instrument without shapes. The stored value is kept either way, for the instrument that takes it again.
 */
internal fun UserPreferences.chordLibraryCapo(): Int? = toolCapo.takeIf { !playsWithoutCapo && chordTuning() != null }

/**
 * The fret below which the Scales tab veils its necks, or null where it draws none a capo lies on, as for
 * [chordLibraryCapo]: the keyboard and Other, drawn on a keyboard, an instrument with no strings to draw, and the
 * fretless violin, which takes no capo (`scaleDiagramGeometryOf`).
 */
internal fun UserPreferences.scaleLibraryCapo(): Int? = toolCapo.takeIf {
    !playsWithoutCapo &&
        instrument != UserPreferences.Instrument.OTHER &&
        instrument != UserPreferences.Instrument.VIOLIN &&
        !instrumentTuning?.strings.isNullOrEmpty()
}
