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
import com.pandulapeter.campfire.data.source.remote.api.SyncFolder
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
 * not move excepted, since it is still in the library somewhere, and so is a song the folder's document has gained since
 * the last synced one, since it may have reached the folder after the run listed it.
 *
 * The preferences are changed before the document is uploaded. The other way round, an upload followed by a write
 * that failed would leave this device's old values looking like changes made here since, and the next run would
 * undo the other devices' with them; this way round, a failed upload only leaves the next run a change to carry.
 */
internal class SyncedPreferencesSync(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val libraryFileLocalSource: LibraryFileLocalSource,
) {

    /**
     * What [synchronize] last wrote into the preferences, which is not a change for [localChanges] to report - until the
     * next emission has been seen, whichever it is, so that a later return to the same values is reported again.
     */
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
        .filter { synced ->
            // Cleared by any emission, not only by its own: where the state conflated the run's write away under a
            // change the user made right after it, the user's value arrives instead and must not leave the marker set.
            val isOwnWrite = synced == writtenBySync
            writtenBySync = null
            !isOwnWrite
        }
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
    suspend fun synchronize(provider: SyncFolder, base: JsonObject?, keptFileNames: Collection<String>): JsonObject? {
        if (userPreferencesRepository.loadUserPreferencesIfNeeded() == null) return null
        // Folded name to this device's spelling, which is the one its screens read the preferences under. Two names of
        // one song only exist on a file system that tells case apart, and the service refuses one of them anyway; the
        // first in sort order is taken, which the descending order leaves last for associateBy to keep.
        val localNames = libraryFileLocalSource.loadLibraryFiles()
            .filter { it.kind == LibraryFileKind.SONG }
            .map { it.name }
            .plus(keptFileNames)
            .sortedDescending()
            .associateBy { it.folded() }
        // The songs the last synced document named, folded. An entry the folder's document holds for any other song was
        // written by another device since then, possibly for a song that reached the folder after this run listed it: it
        // is kept until a run that has seen the song decides about it, or the next run, which has it in its base, prunes
        // it. The base rather than previous, which a conflict moves onto the remote document, so that the retry does not
        // prune the very entry the first attempt kept.
        val baseSongNames = SyncedPreferencesDocument.songNamesOf(base).mapTo(hashSetOf()) { it.folded() }
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
            val arrivingSongNames = SyncedPreferencesDocument.songNamesOf(effectiveRemote)
                .map { it.folded() }
                .filterTo(hashSetOf()) { it !in baseSongNames }
            val snapshot = userPreferencesRepository.userPreferences.first().data?.let(SyncedPreferences::of) ?: return null
            // Each device keeps its own spelling of a file that differs only by case or Unicode form, so all three sides
            // are put on one spelling per song before they are merged, or the merge would read one song as two. The
            // folder's spelling comes first, since every device reads the same folder and so settles on the same one.
            val spellings = mutableMapOf<String, String>().apply {
                SyncedPreferencesDocument.songNamesOf(effectiveRemote).sorted().forEach { getOrPut(it.folded()) { it } }
                SyncedPreferencesDocument.songNamesOf(previous).sorted().forEach { getOrPut(it.folded()) { it } }
                localNames.forEach { (folded, name) -> getOrPut(folded) { name } }
            }
            val spelling = { name: String -> spellings[name.folded()] ?: name }
            val canonicalBase = previous?.let { SyncedPreferencesDocument.withSongsSpelled(it, spelling) }
            val merged = SyncedPreferencesDocument.withSongsWhere(
                document = SyncedPreferencesDocument.merge(
                    base = canonicalBase,
                    local = SyncedPreferencesDocument.localDocument(
                        base = canonicalBase,
                        // A value left under the folder's spelling beside the one under this device's is a leftover
                        // no screen here reads, so the one the user set wins.
                        preferences = snapshot.respelled(spelling, isPreferred = { localNames[it.folded()] == it }),
                    ),
                    remote = effectiveRemote?.let { SyncedPreferencesDocument.withSongsSpelled(it, spelling) },
                ),
                isKept = { it.folded().let { folded -> folded in localNames || folded in arrivingSongNames } },
            )
            // Applied over the snapshot as it was read, so that a value under any other spelling of a song is removed
            // here: compared with a respelled one, such a leftover would look like the merged value and stay.
            val mergedPreferences = SyncedPreferencesDocument.preferencesOf(merged).respelled({ localNames[it.folded()] ?: it })
            if (mergedPreferences != snapshot) {
                userPreferencesRepository.updateUserPreferences { preferences ->
                    mergedPreferences.applyTo(preferences, since = snapshot).also { updated ->
                        // Marked only when the write is the run's alone. One that changes nothing synced emits nothing,
                        // and its marker would swallow the next emission, which can be a change of the user's the
                        // collector has not seen yet; one that kept a value changed here during the merge is what
                        // carries that change, which a conflating state may deliver in place of the user's own emission.
                        val written = SyncedPreferences.of(updated)
                        writtenBySync = written.takeIf { it != SyncedPreferences.of(preferences) && it == mergedPreferences }
                    }
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

    /** See `foldRemoteNamesOntoLocal`: a song's entry is the same song's under any spelling of its file. */
    private fun String.folded() = normalizedToNfc().lowercase()

    private companion object {
        const val MAXIMUM_ATTEMPTS = 2
    }
}
