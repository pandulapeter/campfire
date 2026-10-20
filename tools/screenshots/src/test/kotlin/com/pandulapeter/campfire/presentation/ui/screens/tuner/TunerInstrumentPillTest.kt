/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.tuner

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_guitar
import com.pandulapeter.campfire.presentation.resources.instrument_guitar_strings
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_standard
import com.pandulapeter.campfire.screenshots.openTuner
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class TunerInstrumentPillTest {

    @Test
    fun `a long instrument name beside the reference pitch still leaves its tuning in sight`() = runAppTest {
        openTuner()
        tap(hasText(string(Res.string.instrument_guitar)) and hasClickAction())
        val twelveString = hasText(string(Res.string.instrument_guitar_strings, 12)) and hasClickAction()
        onNode(twelveString).performScrollTo()
        tap(twelveString)
        // The half's padded press is wider than nothing whatever its name is given, so the name's own node is the one to ask.
        val tuning = onNodeWithText(string(Res.string.instrument_tuning_standard), useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(tuning.size.width > 0, "The tuning's name is ${tuning.size.width} px wide.")
    }
}
