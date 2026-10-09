/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.api.model

/**
 * What a sound is read against: the nearest string of [tuning], or the nearest semitone where that is null (chromatic).
 *
 * @param referencePitch A4 in Hz, within `Pitch.REFERENCE_PITCH_RANGE`.
 */
public data class TunerConfig(
    public val referencePitch: Int,
    public val tuning: InstrumentTuning?,
)
