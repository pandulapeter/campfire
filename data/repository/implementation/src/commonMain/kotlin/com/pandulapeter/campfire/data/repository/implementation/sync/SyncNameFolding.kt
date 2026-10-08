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

import com.pandulapeter.campfire.data.model.domain.normalizedToNfc
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider

/**
 * Gives a remote file the local spelling of its name where the two differ only by case.
 *
 * Dropbox paths are case-insensitive, so `Song.cho` here and `song.cho` there are one file to the service and two
 * keys to [SyncPlanner]: the new-file upload of the local one would be refused as a conflict on every pass, and the
 * file would never move. This is a property of one service surfacing in the engine on purpose - it keeps
 * [SyncProvider]'s contract down to a flat folder of names - and it costs a service with exact names nothing, since it
 * only ever renames a remote key that has no exact local match. Every later request uses the local spelling, which
 * the service resolves to the same file. Two *local* names that differ only by case cannot both exist on such a
 * service; the one it refuses is reported as a file that could not be synced, see `upload`.
 */
internal fun foldRemoteNamesOntoLocal(local: List<LocalFileState>, remote: List<RemoteFileState>): List<RemoteFileState> {
    val localKeys = local.mapTo(mutableSetOf()) { it.key }
    val localKeysByFolded = local.associate { it.key.folded() to it.key }
    return remote.map { file ->
        if (file.key in localKeys) file else localKeysByFolded[file.key.folded()]?.let { file.copy(key = it) } ?: file
    }
}

/**
 * Files an index entry under the spelling the listings now have for its file, where the two differ only by case.
 *
 * [foldRemoteNamesOntoLocal] matches the two listings with each other, but the planner looks the index up by exact
 * name as well. A song moved to another spelling of the same name (a file renamed by hand from `Hallelujah.cho`)
 * keeps the old spelling on a service that ignores case, and its entry under whichever spelling the last run saw. Left
 * like that, the planner reads one file as two: an entry whose file is gone from both sides, which it forgets, and a file
 * nobody has seen, which it downloads. A deletion made here then brings the song back, and an edit made elsewhere is
 * taken for a conflict. An entry only moves when its own name is in neither listing and exactly one listed name
 * folds to it and has no entry of its own, so an index that already matches is returned as it is.
 */
internal fun foldIndexNamesOntoListings(
    index: Map<SyncKey, SyncIndexEntry>,
    listed: Set<SyncKey>,
): Map<SyncKey, SyncIndexEntry> {
    val unclaimedByFolded = listed.filter { it !in index }.groupBy { it.folded() }
    val moves = index.keys
        .filter { it !in listed }
        .groupBy { it.folded() }
        .mapNotNull { (folded, orphans) ->
            val candidates = unclaimedByFolded[folded]
            if (orphans.size == 1 && candidates?.size == 1) orphans.single() to candidates.single() else null
        }
    if (moves.isEmpty()) return index
    return index.toMutableMap().apply {
        moves.forEach { (from, to) -> remove(from)?.let { put(to, it) } }
    }
}

/**
 * Two spellings a service takes for one file: case, which Dropbox ignores, and Unicode form, which the file systems
 * disagree about - a name an iPhone hands out decomposed is the same file as the composed one the service holds.
 */
internal fun SyncKey.folded() = copy(name = name.normalizedToNfc().lowercase())
