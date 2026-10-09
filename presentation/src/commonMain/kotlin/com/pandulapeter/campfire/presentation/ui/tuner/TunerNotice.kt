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

/** What the tuner's page says in place of its display, where the microphone cannot be listened to. */
internal enum class TunerNotice {
    NOT_ASKED,
    REFUSED,
    NO_MICROPHONE,
    BUSY,
    NOT_SUPPORTED,
    FAILED,
}

/**
 * The notice for the microphone's [status] and what the tuner says about [listening], null where the display is up: a
 * reason listening stopped for wins, since it is what just happened, then a refusal the platform reports, then a
 * question never asked - which, where the platform cannot say ([MicrophoneStatus.UNKNOWN]), is one not asked by a tap
 * in this run ([hasRequested]).
 */
internal fun tunerNoticeOf(status: MicrophoneStatus, listening: TunerListening, hasRequested: Boolean): TunerNotice? {
    when ((listening as? TunerListening.Stopped)?.reason) {
        TunerStopReason.PERMISSION_DENIED -> return TunerNotice.REFUSED
        TunerStopReason.NO_MICROPHONE, TunerStopReason.MICROPHONE_DISCONNECTED -> return TunerNotice.NO_MICROPHONE
        TunerStopReason.MICROPHONE_BUSY -> return TunerNotice.BUSY
        TunerStopReason.NOT_SUPPORTED -> return TunerNotice.NOT_SUPPORTED
        TunerStopReason.FAILED -> return TunerNotice.FAILED
        null -> Unit
    }
    return when (status) {
        MicrophoneStatus.GRANTED -> null
        MicrophoneStatus.DENIED -> TunerNotice.REFUSED
        MicrophoneStatus.NOT_ASKED -> TunerNotice.NOT_ASKED.takeUnless { hasRequested && listening !is TunerListening.Stopped }
        MicrophoneStatus.UNKNOWN -> TunerNotice.NOT_ASKED.takeUnless { hasRequested }
    }
}

/**
 * Whether the page may open the microphone without a tap: where the platform says it is allowed, or where it cannot say
 * and a tap in this run has asked already. Never where a notice is up, since that is waiting for a tap of its own.
 */
internal fun canListenWithoutTap(status: MicrophoneStatus, hasRequested: Boolean) = when (status) {
    MicrophoneStatus.GRANTED -> true
    MicrophoneStatus.UNKNOWN -> hasRequested
    MicrophoneStatus.NOT_ASKED, MicrophoneStatus.DENIED -> false
}
