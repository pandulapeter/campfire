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

/** How far off a reading is, in steps a listener can act on without hearing every cent go by. */
internal enum class TunerOffset { FAR_FLAT, FLAT, IN_TUNE, SHARP, FAR_SHARP }

/**
 * The step of a reading [cents] off: in tune only once the tracker says so ([isInTune], which waits for the reading to
 * hold), then flat or sharp by its sign, and far beyond [FAR_CENTS] - the point past which a peg is turned rather than
 * nudged.
 */
internal fun tunerOffsetOf(cents: Float, isInTune: Boolean) = when {
    isInTune -> TunerOffset.IN_TUNE
    cents < -FAR_CENTS -> TunerOffset.FAR_FLAT
    cents < 0f -> TunerOffset.FLAT
    cents > FAR_CENTS -> TunerOffset.FAR_SHARP
    else -> TunerOffset.SHARP
}

/** What the tuner announces: the note and its step, see [tunerOffsetOf]. */
internal data class TunerAnnouncement(val note: Int, val offset: TunerOffset)

private const val FAR_CENTS = 15f
