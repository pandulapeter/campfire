/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.catalogue

/** Where asking a catalogue for the songwriters of one [SongDetailsCandidate] is. */
sealed interface SongCreditsState {

    /** The question is being asked. */
    data object Loading : SongCreditsState

    /** The catalogue asked to be waited for, and is being waited for before it is asked again. */
    data object Busy : SongCreditsState

    /** The answer, which may name nobody. */
    data class Loaded(val credits: SongCredits) : SongCreditsState

    /** The catalogue never names songwriters, so it was not asked. */
    data object Unavailable : SongCreditsState

    /** The catalogue could not be reached, or kept refusing. */
    data object Failed : SongCreditsState
}
