/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toDocument
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocument
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.serialization.json.Json

internal class UserPreferencesLocalSourceImpl(
    private val fileStorage: FileStorage
) : UserPreferencesLocalSource {

    /** A document that cannot be parsed is treated as no document at all, so the app starts on its defaults. */
    override suspend fun loadUserPreferences(): UserPreferences = try {
        fileStorage.readText(StorageDirectory.PREFERENCES, FILE_NAME)?.let { json.decodeFromString<UserPreferencesDocument>(it) }
    } catch (exception: Exception) {
        println("Could not read the preferences: ${exception.message}")
        null
    }.let { it ?: UserPreferencesDocument() }.toModel()

    override suspend fun saveUserPreferences(userPreferences: UserPreferences) = fileStorage.writeText(
        directory = StorageDirectory.PREFERENCES,
        name = FILE_NAME,
        text = json.encodeToString(userPreferences.toDocument())
    )

    private companion object {
        const val FILE_NAME = "preferences.json"

        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}
