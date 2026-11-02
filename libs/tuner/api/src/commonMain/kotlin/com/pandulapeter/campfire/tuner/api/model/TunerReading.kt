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
 * One steady reading of what is heard.
 *
 * @param note The target, a MIDI note number: the nearest semitone, or the nearest string of the preset.
 * @param cents How far what is heard is from [note], positive being sharp. Beyond ±50 where a preset's nearest string
 *   is further than half a semitone away.
 * @param frequency What is heard, in Hz.
 * @param isInTune Whether it has been within the in-tune range long enough to be said.
 */
public data class TunerReading(
    public val note: Int,
    public val cents: Float,
    public val frequency: Float,
    public val isInTune: Boolean,
)
