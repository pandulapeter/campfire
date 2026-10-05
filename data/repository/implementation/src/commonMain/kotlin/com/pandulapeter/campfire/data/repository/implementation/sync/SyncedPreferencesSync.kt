/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.normalizedToNfc
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.JsonObject

/**
 * The last step of a run that completed: [SyncedPreferencesDocument] settled between this device and the cloud folder.
 *
 * It comes after the library files, so that the songs the document may name are the ones the run left on both sides,
 * and it is where the preferences of a song deleted on either side are let go of: whatever the document holds for a
 * song that is no longer in the library is dropped from the preferences and the document alike - a song the run could
 * not move excepted, since it is still in the library somewhere.
 *
 * The preferences are changed before the document is uploaded. The other way round, an upload followed by a write
 * that failed would leave this device's old values looking like changes made here since, and the next run would
 * undo the other devices' with them; this way round, a failed upload only leaves the next run a change to carry.
 */
internal class SyncedPreferencesSync(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val libraryFileLocalSource: LibraryFileLocalSource,
) {

    /** What [synchronize] last wrote into the preferences, which is not a change for [localChanges] to report. */
    @Volatile
    private var writtenBySync: SyncedPreferences? = null

    /**
     * Emits whenever the synced part of the preferences is changed by anything but a run, which is what schedules the
     * next one. The first value is the preferences being read, which is not a change.
     */
    val localChanges: Flow<Unit> = userPreferencesRepository.userPreferences
        .mapNotNull { it.data?.let(SyncedPreferences::of) }
        .distinctUntilChanged()
        .drop(1)
        .filter { it != writtenBySync }
        .map { }

    /**
     * Settles the document and answers the one to remember as the last synced, or null where it could not be settled -
     * the preferences unreadable here, or another device writing the document under every attempt - which leaves the
     * previous one in place for the next run.
     *
     * @param base The document the last run left behind, or null where no run has synced it with this account yet.
     * @param keptFileNames The files this run could not move, whose songs keep their preferences although they are not
     *   in the library on this device.
     */
    suspend fun synchronize(provider: SyncProvider, base: JsonObject?, keptFileNames: Collection<String>): JsonObject? {
        if (userPreferencesRepository.loadUserPreferencesIfNeeded() == null) return null
        val songNames = libraryFileLocalSource.loadLibraryFiles()
            .filter { it.kind == LibraryFileKind.SONG }
            .map { it.name }
            .plus(keptFileNames)
            .mapTo(mutableSetOf()) { it.folded() }
        var previous = base
        repeat(MAXIMUM_ATTEMPTS) {
            val remote = provider.downloadDocument(SyncedPreferencesDocument.FILE_NAME)
            val remoteDocument = remote?.bytes?.let(SyncedPreferencesDocument::decode)
            if (remoteDocument != null && SyncedPreferencesDocument.isNewerFormat(remoteDocument)) {
                // The version that bumped the format is the one that has to keep this one's values, so the document is
                // neither applied nor written over. Null would report a failure no retry can mend until the app is
                // updated, and an empty base names no song, so a later run with it only ever adds, as with none.
                println("${SyncedPreferencesDocument.FILE_NAME} was written by a newer version of Campfire; it is left alone.")
                return previous ?: JsonObject(emptyMap())
            }
            // A document that is missing, or is not one this version can read, is taken as unchanged since the last run
            // rather than as one that removed everything: this device's values stay, and are uploaded in its place.
            val effectiveRemote = remoteDocument?.takeIf(SyncedPreferencesDocument::isReadable) ?: previous
            val snapshot = userPreferencesRepository.userPreferences.first().data?.let(SyncedPreferences::of) ?: return null
            val merged = SyncedPreferencesDocument.withSongsWhere(
                document = SyncedPreferencesDocument.merge(
                    base = previous,
                    local = SyncedPreferencesDocument.localDocument(base = previous, preferences = snapshot),
                    remote = effectiveRemote,
                ),
                isKept = { it.folded() in songNames },
            )
            val mergedPreferences = SyncedPreferencesDocument.preferencesOf(merged)
            if (mergedPreferences != snapshot) {
                userPreferencesRepository.updateUserPreferences { preferences ->
                    mergedPreferences.applyTo(preferences, since = snapshot).also { writtenBySync = SyncedPreferences.of(it) }
                }
            }
            if (merged == remoteDocument) return merged
            val result = provider.uploadDocument(
                name = SyncedPreferencesDocument.FILE_NAME,
                bytes = SyncedPreferencesDocument.encode(merged),
                expectedRevision = remote?.revision,
            )
            when (result) {
                is RemoteWriteResult.Written -> return merged
                // Written elsewhere since it was read. The preferences here already hold what the document said then,
                // so that is the base the next attempt merges what was written since against.
                RemoteWriteResult.Conflict -> previous = effectiveRemote
            }
        }
        println("Another device kept writing ${SyncedPreferencesDocument.FILE_NAME}; the next run settles it.")
        return null
    }

    /** See `foldRemoteNamesOntoLocal`: a song's entry stays as long as either spelling of its file is in the library. */
    private fun String.folded() = normalizedToNfc().lowercase()

    private companion object {
        const val MAXIMUM_ATTEMPTS = 2
    }
}
