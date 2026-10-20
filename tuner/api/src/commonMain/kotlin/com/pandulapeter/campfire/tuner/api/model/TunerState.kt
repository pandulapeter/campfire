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
 * Everything the tuner is doing.
 *
 * @param tone The MIDI note of the reference tone sounding, null when none is.
 * @param refusedTones How many tones the platform refused to start since the tuner was made. It only grows, so a caller
 * that just asked for a tone can tell that tone's refusal from one still on its way.
 */
public data class TunerState(
    public val listening: TunerListening = TunerListening.Stopped(),
    public val tone: Int? = null,
    public val refusedTones: Int = 0,
)
