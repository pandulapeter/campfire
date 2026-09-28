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

    /** Replaces the whole document, for writing the preferences as they are rather than changing one of them. */
    suspend fun saveUserPreferences(userPreferences: UserPreferences)

    /**
     * Applies [transform] to the preferences as they are at the moment it runs, which is what every change to one of
     * them goes through: a copy of the whole document the caller read earlier may be missing a change made since,
     * which saving it would undo. Reads the preferences first if they have not been read, and changes nothing if they
     * cannot be. [transform] may be called more than once, so it must not do anything but compute the new value.
     */
    suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences)

    /** Whether anything has ever been saved, see `UserPreferencesLocalSource.hasStoredUserPreferences`. */
    suspend fun hasStoredUserPreferences(): Boolean
}