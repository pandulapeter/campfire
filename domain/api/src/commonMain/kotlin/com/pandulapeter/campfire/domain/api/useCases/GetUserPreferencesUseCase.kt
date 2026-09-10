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

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlinx.coroutines.flow.Flow

interface GetUserPreferencesUseCase {

    /**
     * Separate from [GetScreenDataUseCase], which can only produce anything once the whole library has been read:
     * the theme and the language must not wait for a song scan that has nothing to do with them.
     */
    operator fun invoke(): Flow<DataState<UserPreferences>>
}
