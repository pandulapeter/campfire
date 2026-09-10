/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {

    val userPreferences: Flow<DataState<UserPreferences>>

    /** The saved preferences, or null if they could not be read - see `BaseLocalDataRepository.loadDataIfNeeded`. */
    suspend fun loadUserPreferencesIfNeeded(): UserPreferences?

    suspend fun saveUserPreferences(userPreferences: UserPreferences)
}