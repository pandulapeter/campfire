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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstrumentFamilyTest {

    @Test
    fun `every preset is offered under exactly one instrument of exactly one segment`() {
        val groups = InstrumentFamily.entries.flatMap { it.groups }
        assertEquals(TuningGroup.entries.toSet(), groups.toSet())
        assertEquals(groups.size, groups.toSet().size)
        val offered = groups.flatMap { it.tunings }
        assertEquals(InstrumentTuning.entries.toSet(), offered.toSet())
        assertEquals(offered.size, offered.toSet().size)
    }

    @Test
    fun `a tuning is found under its segment and chromatic under none`() {
        assertEquals(InstrumentFamily.GUITAR, familyOf(InstrumentTuning.GUITAR_EIGHT_STRING_DROP_E))
        assertEquals(InstrumentFamily.BASS, familyOf(InstrumentTuning.BASS_FIVE_STRING))
        assertEquals(InstrumentFamily.MORE, familyOf(InstrumentTuning.VIOLIN))
        assertEquals(TuningGroup.SEVEN_STRING_GUITAR, groupOf(InstrumentTuning.GUITAR_SEVEN_STRING_DROP_A))
        assertNull(familyOf(null))
    }
}
