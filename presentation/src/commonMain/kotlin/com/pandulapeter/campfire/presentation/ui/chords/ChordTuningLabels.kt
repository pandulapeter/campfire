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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.presentation.ui.tuner.tuningLabel
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The tuner's tuning of the same id as [this], which is what names a tuning in the app: the diagrams and the tuner offer
 * one tuning under one name. Null only for the keyboard's, which no tuner tunes.
 */
internal val ChordTuning.instrumentTuning: InstrumentTuning? get() = InstrumentTuning.fromId(id)

/** The name of [tuning] the tuner gives it ("Drop D", "Standard"), its note in the reader's chord [notation]. */
@Composable
internal fun chordTuningLabel(tuning: ChordTuning, notation: ChordNotation) = tuning.instrumentTuning?.let { tuningLabel(it, notation) }.orEmpty()
