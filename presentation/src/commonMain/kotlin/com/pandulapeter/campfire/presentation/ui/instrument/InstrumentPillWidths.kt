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

import kotlin.math.roundToInt

/**
 * The widest the tuning half of an [InstrumentPill] may be, out of [available] for both halves, where the instrument half
 * would be [instrumentWidth] wide uncut: whatever the instrument leaves, but never less than [TUNING_SHARE] of the pill,
 * so that a narrow pill cuts the tuning's name short first and then the instrument's rather than leaving the tuning no
 * room at all.
 */
internal fun tuningHalfMaxWidth(available: Int, instrumentWidth: Int) =
    maxOf(available - instrumentWidth, (available * TUNING_SHARE).roundToInt()).coerceIn(0, maxOf(available, 0))

/** The least share of a narrow [InstrumentPill] its tuning half keeps (see [tuningHalfMaxWidth]). */
internal const val TUNING_SHARE = 0.4f
