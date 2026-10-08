/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import com.pandulapeter.campfire.chordpro.ChordProTime
import com.pandulapeter.campfire.metronome.api.model.TimeSignature

/**
 * The signature a `{time}` value declares, or null for one [ChordProTime] does not read. The two modules check their
 * ranges each on their own, since `:chordpro` depends on nothing, so a signature the constructor would refuse is
 * taken for none rather than trusted to agree.
 */
internal fun timeSignatureOf(time: String?): TimeSignature? = ChordProTime.parse(time)
    ?.takeIf { (beats, unit) -> beats in TimeSignature.BEATS_RANGE && unit in TimeSignature.UNITS }
    ?.let { (beats, unit) -> TimeSignature(beats, unit) }
