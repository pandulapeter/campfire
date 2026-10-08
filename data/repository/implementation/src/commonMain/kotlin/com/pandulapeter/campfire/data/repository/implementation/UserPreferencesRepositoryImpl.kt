/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.repository.implementation.base.WholeDocumentRepository
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import org.koin.core.annotation.Single

@Single
internal class UserPreferencesRepositoryImpl(
    private val userPreferencesLocalSource: UserPreferencesLocalSource,
    override val logger: Logger,
) : WholeDocumentRepository<UserPreferences>(), UserPreferencesRepository {

    override val userPreferences = dataState

    override suspend fun loadDataFromLocalSource() = userPreferencesLocalSource.loadUserPreferences()

    override suspend fun loadUserPreferencesIfNeeded() = loadDataIfNeeded()

    override suspend fun saveUserPreferences(userPreferences: UserPreferences) = writeData(userPreferences) {
        userPreferencesLocalSource.saveUserPreferences(it)
    }

    override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
        loadDataIfNeeded()
        transformAndWriteData(transform) { userPreferencesLocalSource.saveUserPreferences(it) }
    }

    /**
     * Not cached, unlike the preferences themselves: it is asked once as the app starts, and it is the one answer
     * here that a cache would go on repeating after the very first save has made it false.
     */
    override suspend fun hasStoredUserPreferences() = userPreferencesLocalSource.hasStoredUserPreferences()
}
