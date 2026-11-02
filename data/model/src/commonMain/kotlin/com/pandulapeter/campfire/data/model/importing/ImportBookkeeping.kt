/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

/**
 * What an import records beside the files it writes, see `ApplyImportUseCase`: the demo library's names where the import
 * plants it, and the shared links among its batches.
 *
 * @param demoSongFileNames The demo's song files, whose names the import writes are recorded as the demo's.
 * @param demoSetlistFileNames The same for the demo's setlist.
 */
data class ImportBookkeeping(
    val demoSongFileNames: Set<String>? = null,
    val demoSetlistFileNames: Set<String>? = null,
    val sharedLinkParts: List<SharedLinkPart> = emptyList(),
)

/**
 * One shared link among the batches of a review, and the entries of its plan that it brought: what an import of it
 * writes under their names is remembered for that link alone (see [sharedLinkChoices]), since another link, or the
 * files added beside it, never wrote those names for it.
 */
class SharedLinkPart(
    val fileId: String,
    val songIndices: IntRange,
    val setlistIndices: IntRange,
) {

    /** The part of [plan] this link brought. */
    fun of(plan: ImportPlan) = plan.copy(songs = plan.songs.slice(songIndices), setlists = plan.setlists.slice(setlistIndices))
}
