/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.implementation

import com.pandulapeter.campfire.tuner.api.model.TunerReading

/** What the display shows: the steady [reading], null while nothing is heard, and whether the input is digitally [isSilent]. */
internal data class TrackedPitch(
    val reading: TunerReading?,
    val isSilent: Boolean,
)
