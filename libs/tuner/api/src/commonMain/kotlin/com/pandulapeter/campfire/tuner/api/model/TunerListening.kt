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

/** Whether the microphone is open, and what it hears. */
public sealed interface TunerListening {

    /** Closed. [reason] says why it closed (or did not open) on its own, and is null after `Tuner.stopListening`. */
    public data class Stopped(public val reason: TunerStopReason? = null) : TunerListening

    /** Being opened, which on the web waits for the browser's answer. */
    public data object Starting : TunerListening

    /**
     * Open. [reading] is null while no clear note is heard (or a tone is sounding), and [issue] says what keeps the
     * input from hearing anything at all.
     */
    public data class Hearing(
        public val reading: TunerReading? = null,
        public val issue: TunerInputIssue? = null,
    ) : TunerListening
}
