/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MicrophonePermissionDecisionTest {

    @Test
    fun `a grant clears what earlier answers stored`() {
        assertEquals(
            MicrophoneRefusal(),
            microphoneRefusalAfter(rationaleBefore = true, rationaleAfter = false, isGranted = true, earlierSilentDenials = 1),
        )
    }

    @Test
    fun `a denial after a rationale with none after it is a refusal for good`() {
        assertTrue(microphoneRefusalAfter(rationaleBefore = true, rationaleAfter = false, isGranted = false, earlierSilentDenials = 0).isForGood)
    }

    @Test
    fun `a first silent denial is not a refusal for good but a second in a row is`() {
        val first = microphoneRefusalAfter(rationaleBefore = false, rationaleAfter = false, isGranted = false, earlierSilentDenials = 0)
        assertFalse(first.isForGood)
        val second = microphoneRefusalAfter(rationaleBefore = false, rationaleAfter = false, isGranted = false, earlierSilentDenials = first.silentDenials)
        assertTrue(second.isForGood)
    }

    @Test
    fun `a denial with a rationale after it can still be asked again`() {
        assertEquals(
            MicrophoneRefusal(),
            microphoneRefusalAfter(rationaleBefore = false, rationaleAfter = true, isGranted = false, earlierSilentDenials = 1),
        )
    }

    @Test
    fun `a lapsed grant with nothing stored is a question never asked that can be asked`() {
        assertEquals(
            AndroidMicrophoneState(MicrophoneStatus.NOT_ASKED, canAskAgain = true),
            androidMicrophoneStateOf(isGranted = false, rationale = false, isRefusedForGood = false),
        )
    }

    @Test
    fun `a stored refusal for good with no rationale offers only the settings`() {
        assertEquals(
            AndroidMicrophoneState(MicrophoneStatus.DENIED, canAskAgain = false),
            androidMicrophoneStateOf(isGranted = false, rationale = false, isRefusedForGood = true),
        )
    }

    @Test
    fun `a rationale owed is a refusal that can be asked again`() {
        assertEquals(
            AndroidMicrophoneState(MicrophoneStatus.DENIED, canAskAgain = true),
            androidMicrophoneStateOf(isGranted = false, rationale = true, isRefusedForGood = true),
        )
    }

    @Test
    fun `a grant is granted whatever was stored`() {
        assertEquals(
            AndroidMicrophoneState(MicrophoneStatus.GRANTED, canAskAgain = true),
            androidMicrophoneStateOf(isGranted = true, rationale = false, isRefusedForGood = true),
        )
    }
}
