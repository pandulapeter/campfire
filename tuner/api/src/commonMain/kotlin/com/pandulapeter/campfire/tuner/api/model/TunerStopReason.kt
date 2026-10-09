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

/** Why listening stopped, or never started, on its own. */
public enum class TunerStopReason {
    /** The platform does not allow the app the microphone. */
    PERMISSION_DENIED,

    /** There is no input device. */
    NO_MICROPHONE,

    /** A call or another app holds the microphone. */
    MICROPHONE_BUSY,

    /** The input went away while it was being listened to. */
    MICROPHONE_DISCONNECTED,

    /** The platform cannot record at all: a page outside a secure context, a browser without `getUserMedia`. */
    NOT_SUPPORTED,

    /** Anything else. */
    FAILED,
}
