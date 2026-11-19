/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

/**
 * One folder of files sync moves, the same on both sides: [id] names the folder in the cloud and the first half of a
 * file's key in the sync index (`<id>/<name>`), so an id never changes once a release has synced with it.
 *
 * @param matches Whether a name is a file of this collection. Both of the folders sync compares are ones the user can
 *   open (the library folder on the desktop, the app folder of their cloud storage everywhere), so either may hold
 *   whatever else they keep there, and that is left where it is. Every listing of either side has to ask this one
 *   question: a file that one side lists and the other leaves out looks to a sync run exactly like a file that was
 *   deleted there.
 * @param maxSize The largest file of the collection sync reads or downloads, in bytes: every file is held in memory
 *   whole. A larger one is left where it is and named as a failure.
 * @param role What the files are to the user, which decides the rules sync applies to them.
 */
class SyncCollection(
    val id: String,
    val matches: (name: String) -> Boolean,
    val maxSize: Long,
    val role: Role,
) {

    /** Whether files of this collection are what the user keeps rather than what follows from it, see [Role.DOCUMENT]. */
    val isDocument get() = role == Role.DOCUMENT

    override fun toString() = id

    enum class Role {

        /**
         * What the user keeps and edits: the questions about deleting much of a library count only these, and a file
         * changed on both sides is a conflict, resolved by keeping both.
         */
        DOCUMENT,

        /**
         * Named by its content, so the same name never holds two versions: never a conflict, compared by size rather
         * than hashed, kept, synced, and deleted with the documents that name it, and brought back from the cloud
         * folder's history when one names it again after it was deleted everywhere.
         */
        CONTENT_FILE,
    }
}
