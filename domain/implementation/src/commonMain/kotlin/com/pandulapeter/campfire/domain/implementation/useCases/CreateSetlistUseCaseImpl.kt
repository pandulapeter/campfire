/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.domain.api.useCases.CreateSetlistUseCase
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Factory

@Factory
class CreateSetlistUseCaseImpl internal constructor(
    private val setlistRepository: SetlistRepository,
) : CreateSetlistUseCase {

    override suspend operator fun invoke(title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = setlistRepository.createSetlist(
        title = title.trim(),
        description = description.trim(),
        date = date,
        isCountdownShown = isCountdownShown,
    )
}
