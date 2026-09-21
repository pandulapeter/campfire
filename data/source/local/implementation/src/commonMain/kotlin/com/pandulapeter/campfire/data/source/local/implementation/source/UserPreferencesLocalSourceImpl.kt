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
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocumentFormat
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Single

@Single
internal class UserPreferencesLocalSourceImpl(
    private val fileStorage: FileStorage,
) : UserPreferencesLocalSource {

    /**
     * Only a document that is *absent* means the defaults. One that is there and cannot be read throws, so that the
     * repository reports a failed read and tries again, rather than handing out defaults the next save would write
     * over a file that may be perfectly good. One that can be read and does not decode as it is gives up only the
     * fields that are wrong, and is copied aside first, since the next save is the end of whatever was in them.
     */
    override suspend fun loadUserPreferences(): UserPreferences {
        val text = fileStorage.readText(StorageDirectory.PREFERENCES, FILE_NAME) ?: return UserPreferencesDocument().toModel()
        val decoded = UserPreferencesDocumentFormat.decode(text)
        if (!decoded.isIntact && text.isNotBlank()) keepUnreadableDocument()
        return decoded.document.toModel()
    }

    override suspend fun saveUserPreferences(userPreferences: UserPreferences) = fileStorage.writeText(
        directory = StorageDirectory.PREFERENCES,
        name = FILE_NAME,
        text = UserPreferencesDocumentFormat.encode(userPreferences.toDocument()),
    )

    override suspend fun hasStoredUserPreferences() = fileStorage.exists(StorageDirectory.PREFERENCES, FILE_NAME)

    /**
     * The bytes rather than the text that was read, so the copy is the file and not a decoding of it. Failing to keep
     * it is no reason to fail the read: the app would then not start on the settings it *could* read.
     */
    private suspend fun keepUnreadableDocument() = try {
        fileStorage.readBytes(StorageDirectory.PREFERENCES, FILE_NAME)?.let {
            fileStorage.writeBytes(StorageDirectory.PREFERENCES, UNREADABLE_FILE_NAME, it)
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not keep a copy of the preferences: ${exception.message}")
    }

    private companion object {
        const val FILE_NAME = "preferences.json"
        const val UNREADABLE_FILE_NAME = "preferences.json.bad"
    }
}
