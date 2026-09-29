/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.datetime.LocalDate

interface CreateSetlistUseCase {

    /**
     * Writes a new, empty setlist file and returns the setlist it became. [description] may be blank. [date] is the
     * day the setlist is for, which is the day it is created unless the user picked another one, and
     * [isCountdownShown] whether the setlist's header counts down to it.
     */
    suspend operator fun invoke(title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist
}
