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
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteFile
import kotlinx.serialization.json.JsonObject

/**
 * What one pass may touch, worked out from the two listings and the index before anything is planned: pure, and the
 * part of a run that decides which files it may delete.
 *
 * @param local The files of this device that could be read.
 * @param remote The files of the cloud folder, under the spelling this device uses for them.
 * @param index What the last run saw, under the same spellings, without the files left out on both sides.
 * @param planningIndex [index] without the files this device could not read, which is what the plan is made from.
 * @param caseCollisions Local names a service that ignores case takes for one file.
 * @param unstorable The cloud folder's library files whose names this device cannot hold.
 * @param syncedPreferences The last synced `preferences.json`, without the songs a `KEEP_AND_UPLOAD` makes new here.
 */
internal data class PreparedPass(
    val local: List<LocalFileState>,
    val remote: List<RemoteFileState>,
    val index: Map<SyncKey, SyncIndexEntry>,
    val planningIndex: Map<SyncKey, SyncIndexEntry>,
    val caseCollisions: Set<SyncKey>,
    val unstorable: List<String>,
    val syncedPreferences: JsonObject?,
)

/**
 * @param tooLarge The local files too large to read, left out on both sides with their index entries.
 * @param unreadable The local files whose read failed, whose index entries are kept for the run that can read them.
 */
internal fun preparePass(
    index: Map<SyncKey, SyncIndexEntry>,
    listed: List<RemoteFile>,
    local: List<LocalFileState>,
    tooLarge: Set<SyncKey>,
    unreadable: Set<SyncKey>,
    canHoldFileName: (LibraryFileKind, String) -> Boolean,
    deletionPolicy: SyncDeletionPolicy,
    syncedPreferences: JsonObject?,
): PreparedPass {
    var index = index
    var syncedPreferences = syncedPreferences
    // Neither a file too large to read nor one that could not be read is absent, and both are folded onto
    // below, so that a remote spelling of one is recognized as that file and left out with it.
    val excluded = tooLarge + unreadable
    // Names a service that ignores case takes for one file. Only ever consulted once such a service has refused
    // one of them, so a service with exact names never notices.
    val caseCollisions = local.groupBy { it.key.folded() }.values
        .filter { it.size > 1 }
        .flatten()
        .mapTo(mutableSetOf()) { it.key }
    // The rule the local listing applies, applied here rather than in a provider so that every provider gets
    // it: a file listed on one side only is a deletion as far as the planner can tell.
    val (storable, unstorable) = listed
        .filter { it.kind.matches(it.name) }
        .partition { canHoldFileName(it.kind, it.name) }
    val unstorableKeys = unstorable.mapTo(mutableSetOf()) { SyncKey(kind = it.kind, name = it.name) }
    val remote = foldRemoteNamesOntoLocal(
        local = local + excluded.map { LocalFileState(key = it, hash = "") },
        remote = storable.map {
            RemoteFileState(
                key = SyncKey(kind = it.kind, name = it.name),
                revision = it.revision,
                contentHash = it.contentHash,
                size = it.size,
            )
        },
    ).filterNot { it.key in excluded }
    // After the listings have been matched with each other, so that an entry follows the spelling the plan
    // uses for its file.
    index = foldIndexNamesOntoListings(
        index = index,
        listed = (local.map { it.key } + excluded + remote.map { it.key }).toSet(),
    )
    // A file too large to read, or one this device cannot store, is left out on both sides, its index entry
    // included: with the entry kept, the planner would see a file gone here and unchanged there, and delete the
    // remote copy. Without one, the day the file is small enough again, or renamed to something this device can
    // hold, it is on both sides with nothing to say which is newer, which the planner settles by content.
    index = index - tooLarge - unstorableKeys
    if (deletionPolicy == SyncDeletionPolicy.KEEP_AND_UPLOAD) {
        // Forgetting that the last run saw these files is what makes them new on this device: a file that is
        // here, is not there and has no index entry is planned as an upload.
        val localKeys = local.mapTo(mutableSetOf()) { it.key }
        val remoteKeys = remote.mapTo(mutableSetOf()) { it.key }
        val forgottenSongs = index.keys
            .filter { it.kind == LibraryFileKind.SONG && it in localKeys && it !in remoteKeys }
            .mapTo(mutableSetOf()) { LibraryFiles.identityKey(it.name) }
        index = index.filterKeys { it !in localKeys || it in remoteKeys }
        // The same for what is set for those songs in preferences.json: with the base still naming them, the
        // preferences step would read the folder's document, which another device emptied with its library,
        // as removing their overrides, while this device was asked to keep the songs and so keeps those too.
        syncedPreferences = syncedPreferences?.let { base ->
            SyncedPreferencesDocument.withSongsWhere(base) { LibraryFiles.identityKey(it) !in forgottenSongs }
        }
    }
    if (deletionPolicy == SyncDeletionPolicy.KEEP_AND_DOWNLOAD) {
        // The same the other way round: a file that is there, is not here and has no index entry is a
        // download, which is how the folder's files come back onto a device that lost its library folder.
        val localKeys = local.mapTo(mutableSetOf()) { it.key }
        val remoteKeys = remote.mapTo(mutableSetOf()) { it.key }
        index = index.filterKeys { it !in remoteKeys || it in localKeys }
    }
    // The entry of a file that could not be read is kept for the run that can read it, but the planner does
    // not see it: with it, a file gone from this side's listing and unchanged on the other is a deletion.
    val planningIndex = index - unreadable
    return PreparedPass(
        local = local,
        remote = remote,
        index = index,
        planningIndex = planningIndex,
        caseCollisions = caseCollisions,
        unstorable = unstorable.map { it.name },
        syncedPreferences = syncedPreferences,
    )
}
