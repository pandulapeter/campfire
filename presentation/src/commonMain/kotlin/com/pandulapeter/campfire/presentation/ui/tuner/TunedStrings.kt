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

import com.pandulapeter.campfire.tuner.api.model.TunerListening

/**
 * The notes heard in tune so far, which the strings row ticks off: [previous] grown by a reading the tracker calls in
 * tune, kept while the microphone is being opened, and forgotten once it closes, since a string that was in tune when
 * the instrument was put down is not known to be in tune when it is picked up again.
 */
internal fun tunedNotesAfter(previous: Set<Int>, listening: TunerListening): Set<Int> = when (listening) {
    is TunerListening.Stopped -> emptySet()
    is TunerListening.Starting -> previous
    is TunerListening.Hearing -> listening.reading?.takeIf { it.isInTune }?.let { previous + it.note } ?: previous
}
