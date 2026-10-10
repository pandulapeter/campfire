/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The segments of [InstrumentPicker], each switching the chips under it to the [groups] it holds. There are only a few
 * of them because a phone fits about five segments: a guitar's and a bass's string counts are headings under one
 * segment rather than segments of their own, and the instruments with few tunings share [MORE]. Chromatic tuning is
 * the segment before them, with no family and a single chip.
 */
internal enum class InstrumentFamily(
    val groups: List<TuningGroup>,
) {
    GUITAR(listOf(TuningGroup.SIX_STRING_GUITAR, TuningGroup.SEVEN_STRING_GUITAR, TuningGroup.EIGHT_STRING_GUITAR)),
    BASS(listOf(TuningGroup.FOUR_STRING_BASS, TuningGroup.FIVE_STRING_BASS)),
    UKULELE(listOf(TuningGroup.UKULELE)),
    MORE(listOf(TuningGroup.BANJO, TuningGroup.VIOLIN, TuningGroup.MANDOLIN)),
}

/** The segment [tuning] is offered under, null for chromatic tuning. */
internal fun familyOf(tuning: InstrumentTuning?) = InstrumentFamily.entries.firstOrNull { family ->
    family.groups.any { tuning in it.tunings }
}

/** The instrument [tuning] is a tuning of. */
internal fun groupOf(tuning: InstrumentTuning) = TuningGroup.entries.first { tuning in it.tunings }
