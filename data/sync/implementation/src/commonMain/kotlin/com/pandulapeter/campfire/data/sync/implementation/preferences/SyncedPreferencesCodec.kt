/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.preferences

import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.model.library.LibrarySyncSteps
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.sync.implementation.merge.MergedFolderDocumentCodec
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject

/**
 * Campfire's folder document: [SyncedPreferencesDocument], its entries keyed by song file names, over the synced part
 * of [UserPreferencesRepository]'s preferences ([SyncedPreferences]).
 */
internal class SyncedPreferencesCodec(
    private val userPreferencesRepository: UserPreferencesRepository,
) : MergedFolderDocumentCodec<SyncedPreferences> {

    override val id = LibrarySyncSteps.PREFERENCES

    override val fileName = SyncedPreferencesDocument.FILE_NAME

    override val ownedFields = SyncedPreferencesDocument.OWNED_FIELDS

    override val ownedFieldsWhenUnrecorded = SyncedPreferencesDocument.OWNED_FIELDS_OF_4_7_0

    override val keyedBy = LibraryCollections.SONGS

    override fun isReadable(document: JsonObject) = SyncedPreferencesDocument.isReadable(document)

    override fun isNewerFormat(document: JsonObject) = SyncedPreferencesDocument.isNewerFormat(document)

    override fun keysOf(document: JsonObject?) = SyncedPreferencesDocument.songNamesOf(document)

    override fun withKeysSpelled(document: JsonObject, spelling: (String) -> String) =
        SyncedPreferencesDocument.withSongsSpelled(document, spelling)

    override fun withKeysWhere(document: JsonObject, isKept: (String) -> Boolean) = SyncedPreferencesDocument.withSongsWhere(document, isKept)

    override fun withoutFieldsNotOwnedBy(base: JsonObject, ownedByWriter: Set<String>) =
        SyncedPreferencesDocument.withoutFieldsNotOwnedBy(base, ownedByWriter)

    override fun merge(base: JsonObject?, local: JsonObject, remote: JsonObject?) = SyncedPreferencesDocument.merge(base, local, remote)

    override fun localDocument(base: JsonObject?, values: SyncedPreferences) = SyncedPreferencesDocument.localDocument(base, values)

    override fun valuesOf(document: JsonObject) = SyncedPreferencesDocument.preferencesOf(document)

    override fun respelled(values: SyncedPreferences, spelling: (String) -> String, isPreferred: (String) -> Boolean) =
        values.respelled(spelling, isPreferred)

    override val values = userPreferencesRepository.userPreferences.map { it.data?.let(SyncedPreferences::of) }

    override suspend fun load() = userPreferencesRepository.loadUserPreferencesIfNeeded() != null

    override suspend fun apply(values: SyncedPreferences, since: SyncedPreferences, onWritten: (before: SyncedPreferences, after: SyncedPreferences) -> Unit) =
        userPreferencesRepository.updateUserPreferences { preferences ->
            values.applyTo(preferences, since = since).also { updated -> onWritten(SyncedPreferences.of(preferences), SyncedPreferences.of(updated)) }
        }
}
