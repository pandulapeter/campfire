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

/** One entry of an [ImportPlan], by its place in [ImportPlan.songs] or [ImportPlan.setlists]; names are not unique within a batch. */
data class ImportEntryKey(
    val kind: Kind,
    val index: Int,
) {
    enum class Kind {
        SONG,
        SETLIST,
    }
}

/**
 * What the review of an [ImportPlan] answered: which entries go in, and what each one whose name is taken does.
 *
 * A choice made on one row outlives a change of the choice for all of them, even when it comes to say the same thing,
 * since it is the answer the user gave about that one entry. The review's header for all of them sets the rows it shows
 * as well, though, so that what it reads and what it does are what the rows come to.
 */
data class ImportChoices(
    /** Entries the user unticked; a repeat of one of them follows it out. */
    val leftOut: Set<ImportEntryKey> = emptySet(),
    /** What every conflicting entry without a choice of its own gets. */
    val conflictResolution: ImportConflictResolution = ImportConflictResolution.KEEP_BOTH,
    /** The rows the user set by hand, kept when [conflictResolution] changes. */
    val conflictResolutions: Map<ImportEntryKey, ImportConflictResolution> = emptyMap(),
    /**
     * Tags added to every song the import writes, next to the ones its file carries; a song already in the library, and
     * so not written, keeps its own.
     */
    val tags: List<String> = emptyList(),
) {

    fun resolutionOf(key: ImportEntryKey) = conflictResolutions[key] ?: conflictResolution

    fun isChosen(key: ImportEntryKey) = key !in leftOut
}
