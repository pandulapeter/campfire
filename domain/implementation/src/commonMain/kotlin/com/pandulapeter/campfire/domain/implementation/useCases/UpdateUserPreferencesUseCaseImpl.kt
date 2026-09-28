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

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.useCases.UpdateUserPreferencesUseCase
import org.koin.core.annotation.Factory

@Factory
class UpdateUserPreferencesUseCaseImpl internal constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : UpdateUserPreferencesUseCase {

    override suspend operator fun invoke(update: (UserPreferences) -> UserPreferences) = userPreferencesRepository.updateUserPreferences(update)
}
