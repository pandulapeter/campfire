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

import com.pandulapeter.campfire.presentation.ui.platform.MicrophoneStatus
import com.pandulapeter.campfire.tuner.api.model.TunerListening
import com.pandulapeter.campfire.tuner.api.model.TunerStopReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TunerNoticeTest {

    private val stopped = TunerListening.Stopped()
    private val hearing = TunerListening.Hearing()

    @Test
    fun `a granted microphone shows the display`() {
        assertNull(tunerNoticeOf(MicrophoneStatus.GRANTED, hearing, hasRequested = false))
        assertNull(tunerNoticeOf(MicrophoneStatus.GRANTED, stopped, hasRequested = false))
    }

    @Test
    fun `a question never asked offers the microphone`() {
        assertEquals(TunerNotice.NOT_ASKED, tunerNoticeOf(MicrophoneStatus.NOT_ASKED, stopped, hasRequested = false))
        assertEquals(TunerNotice.NOT_ASKED, tunerNoticeOf(MicrophoneStatus.UNKNOWN, stopped, hasRequested = false))
    }

    @Test
    fun `where the platform cannot say, a tap on this device is the answer until listening says otherwise`() {
        assertNull(tunerNoticeOf(MicrophoneStatus.UNKNOWN, stopped, hasRequested = true))
        assertNull(tunerNoticeOf(MicrophoneStatus.UNKNOWN, TunerListening.Starting, hasRequested = true))
        assertNull(tunerNoticeOf(MicrophoneStatus.UNKNOWN, hearing, hasRequested = true))
        assertEquals(TunerNotice.REFUSED, tunerNoticeOf(MicrophoneStatus.UNKNOWN, TunerListening.Stopped(TunerStopReason.PERMISSION_DENIED), hasRequested = true))
    }

    @Test
    fun `a refusal is said whichever side reports it`() {
        assertEquals(TunerNotice.REFUSED, tunerNoticeOf(MicrophoneStatus.DENIED, stopped, hasRequested = true))
        assertEquals(TunerNotice.REFUSED, tunerNoticeOf(MicrophoneStatus.NOT_ASKED, TunerListening.Stopped(TunerStopReason.PERMISSION_DENIED), hasRequested = false))
    }

    @Test
    fun `a reason listening stopped for wins over a granted microphone`() {
        assertEquals(TunerNotice.BUSY, tunerNoticeOf(MicrophoneStatus.GRANTED, TunerListening.Stopped(TunerStopReason.MICROPHONE_BUSY), hasRequested = false))
        assertEquals(TunerNotice.NO_MICROPHONE, tunerNoticeOf(MicrophoneStatus.GRANTED, TunerListening.Stopped(TunerStopReason.MICROPHONE_DISCONNECTED), hasRequested = false))
        assertEquals(TunerNotice.NOT_SUPPORTED, tunerNoticeOf(MicrophoneStatus.UNKNOWN, TunerListening.Stopped(TunerStopReason.NOT_SUPPORTED), hasRequested = true))
        assertEquals(TunerNotice.FAILED, tunerNoticeOf(MicrophoneStatus.GRANTED, TunerListening.Stopped(TunerStopReason.FAILED), hasRequested = false))
    }

    @Test
    fun `the microphone is only opened without a tap where it is known to be allowed or a tap asked already`() {
        assertTrue(canListenWithoutTap(MicrophoneStatus.GRANTED, hasRequested = false))
        assertFalse(canListenWithoutTap(MicrophoneStatus.UNKNOWN, hasRequested = false))
        assertTrue(canListenWithoutTap(MicrophoneStatus.UNKNOWN, hasRequested = true))
        assertFalse(canListenWithoutTap(MicrophoneStatus.NOT_ASKED, hasRequested = true))
        assertFalse(canListenWithoutTap(MicrophoneStatus.DENIED, hasRequested = true))
    }
}
