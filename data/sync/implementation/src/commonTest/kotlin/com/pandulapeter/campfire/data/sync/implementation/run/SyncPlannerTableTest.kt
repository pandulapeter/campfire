/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteFile
import com.pandulapeter.campfire.data.sync.implementation.LocalFileState
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.SyncOperation
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every state a file can be in when a pass is planned, enumerated rather than sampled: here and in the folder each
 * absent, unchanged, or changed against an index entry that is there or not, for a song, a setlist, and a picture, alone
 * and beside a second file under another spelling of its name or another name holding the same or other bytes (for a
 * picture, another of the same size or not, which is all its index entry knows). Each case goes through what a pass
 * does before planning (`preparePass` folds the names) and is held to the promises that keep a library from being lost,
 * judged on what the plan does to the files: a deletion only of a version the last run saw on that side, never one
 * version deleted on both sides, never a picture a song here names, and never a file left missing on a side where the
 * other changed it. [SyncPlannerTest] holds the readable examples.
 *
 * The folder holds at most one spelling of a name in every case, since every provider takes two spellings for one file
 * (the `SyncFolder` contract). A picture has one spelling only: its name is lowercase hex, and any other spelling is
 * not a library file to either listing.
 */
class SyncPlannerTableTest {

    @Test
    fun `every state of a song or a setlist is planned safely`() = assertSafe(
        DOCUMENTS.flatMap { (key, _) -> statesOf(key, CONTENT.first()).map { case(listOf(it)) } },
    )

    @Test
    fun `every state beside another spelling of the name or a rename is planned safely`() = assertSafe(
        DOCUMENTS.flatMap { (first, otherNames) ->
            otherNames.flatMap { second ->
                CONTENT.indices.flatMap { secondContent ->
                    statesOf(first, CONTENT.first()).flatMap { firstState ->
                        statesOf(second, CONTENT[secondContent])
                            .filter { firstState.remote == State.ABSENT || it.remote == State.ABSENT || first.folded() != second.folded() }
                            .map { case(listOf(firstState, it)) }
                    }
                }
            }
        },
    )

    @Test
    fun `every state of a picture is planned safely`() = assertSafe(
        statesOf(FIRST_PICTURE, PICTURE_CONTENT.first()).flatMap { picture ->
            Naming.entries.filter { it != Naming.BOTH }.flatMap { naming ->
                OtherSong.entries.map { otherSong -> pictureCase(listOf(picture), naming, otherSong) }
            }
        },
    )

    @Test
    fun `every state of two pictures of one size or two is planned safely`() = assertSafe(
        PICTURE_CONTENT.indices.flatMap { secondContent ->
            statesOf(FIRST_PICTURE, PICTURE_CONTENT.first()).flatMap { first ->
                statesOf(SECOND_PICTURE, PICTURE_CONTENT[secondContent]).flatMap { second ->
                    Naming.entries.map { naming -> pictureCase(listOf(first, second), naming, OtherSong.STEADY) }
                }
            }
        },
    )

    private enum class State { ABSENT, UNCHANGED, CHANGED }

    /** Which of the pictures of a case the steady song here names, or none known because a song could not be read. */
    private enum class Naming { NONE, FIRST, BOTH, UNKNOWN }

    /** A second song beside the one naming the pictures, which is what decides whether this pass may delete one. */
    private enum class OtherSong(val local: State, val remote: State, val isIndexed: Boolean) {
        STEADY(State.UNCHANGED, State.UNCHANGED, true),
        ARRIVING(State.ABSENT, State.UNCHANGED, false),
        LEAVING_HERE(State.UNCHANGED, State.ABSENT, true),
        LEAVING_THERE(State.ABSENT, State.UNCHANGED, true),
    }

    /** The version an index entry knows a file by, and the one an edit on either side makes of it. */
    private data class Content(val hash: String, val changedHash: String, val revision: String)

    private data class FileState(val key: SyncKey, val local: State, val remote: State, val isIndexed: Boolean, val content: Content) {

        val localHash = when (local) {
            State.ABSENT -> null
            State.UNCHANGED -> content.hash
            State.CHANGED -> content.changedHash
        }

        val remoteRevision = when (remote) {
            State.ABSENT -> null
            State.UNCHANGED -> content.revision
            State.CHANGED -> "${content.revision}+"
        }

        override fun toString() = "${key.name} here ${local.name.lowercase()}, there ${remote.name.lowercase()}, " +
            if (isIndexed) "indexed" else "not indexed"
    }

    private class Case(
        val description: String,
        val files: List<FileState>,
        val imageReferences: Map<SyncKey, Set<String>>?,
    )

    private fun statesOf(key: SyncKey, content: Content) = State.entries.flatMap { local ->
        State.entries.flatMap { remote ->
            listOf(true, false).map { isIndexed -> FileState(key, local, remote, isIndexed, content) }
        }
    }

    private fun case(files: List<FileState>) = Case(description = files.joinToString("; "), files = files, imageReferences = emptyMap())

    private fun pictureCase(pictures: List<FileState>, naming: Naming, otherSong: OtherSong): Case {
        val namingSong = FileState(NAMING_SONG, State.UNCHANGED, State.UNCHANGED, isIndexed = true, content = CONTENT.first())
        val other = FileState(OTHER_SONG, otherSong.local, otherSong.remote, otherSong.isIndexed, CONTENT.last())
        val named = when (naming) {
            Naming.NONE, Naming.UNKNOWN -> emptySet()
            Naming.FIRST -> setOf(FIRST_PICTURE.name)
            Naming.BOTH -> setOf(FIRST_PICTURE.name, SECOND_PICTURE.name)
        }
        val references = mapOf(NAMING_SONG to named) + if (other.localHash != null) mapOf(OTHER_SONG to emptySet()) else emptyMap()
        return Case(
            description = "${pictures.joinToString("; ")}; named: ${naming.name.lowercase()}; other song ${otherSong.name.lowercase()}",
            files = pictures + namingSong + other,
            imageReferences = references.takeIf { naming != Naming.UNKNOWN },
        )
    }

    private fun assertSafe(cases: List<Case>) {
        val failures = cases.flatMap { case -> violationsOf(case).map { "${case.description}: $it" } }
        assertTrue(
            actual = failures.isEmpty(),
            message = "${failures.size} unsafe plans in ${cases.size} cases:\n" + failures.take(MAXIMUM_REPORTED).joinToString("\n"),
        )
    }

    private fun violationsOf(case: Case): List<String> {
        val pass = preparePass(
            index = case.files.filter { it.isIndexed }.associate { it.key to SyncIndexEntry(it.content.hash, it.content.revision) },
            listed = case.files.mapNotNull { file ->
                file.remoteRevision?.let { RemoteFile(kind = file.key.kind, name = file.key.name, revision = it, contentHash = null, size = 0) }
            },
            local = case.files.mapNotNull { file -> file.localHash?.let { LocalFileState(file.key, it) } },
            tooLarge = emptySet(),
            unreadable = emptySet(),
            canHoldFileName = { _, _ -> true },
            deletionPolicy = SyncDeletionPolicy.ASK,
            syncedPreferences = null,
        )
        val operations = SyncPlanner.planned(
            local = pass.local,
            remote = pass.remote,
            index = pass.planningIndex,
            imageReferences = case.imageReferences,
        ).operations
        // Judged on what the planner was given, the names already folded: the requests use these spellings, which the
        // provider resolves to the listed file.
        return PlannedFiles(
            local = pass.local.associate { it.key to it.hash },
            remote = pass.remote.associate { it.key to it.revision },
            index = pass.planningIndex,
            named = case.imageReferences?.values?.flatten()?.toSet(),
        ).violationsOf(operations)
    }

    /** The planner's view of one case: [named] is null where a song here could not be read. */
    private class PlannedFiles(
        val local: Map<SyncKey, String>,
        val remote: Map<SyncKey, String>,
        val index: Map<SyncKey, SyncIndexEntry>,
        val named: Set<String>?,
    ) {

        fun violationsOf(operations: List<SyncOperation>) = buildList {
            val deletedHere = operations.filterIsInstance<SyncOperation.DeleteLocal>().map { it.key }
            val deletedThere = operations.filterIsInstance<SyncOperation.DeleteRemote>().map { it.key }
            deletedHere.forEach { key ->
                val entry = index[key]
                if (entry == null) add("deletes $key here, which the index never saw")
                if (entry != null && local[key] != entry.localHash) add("deletes $key here, changed since the last run")
            }
            deletedThere.forEach { key ->
                val entry = index[key]
                if (entry == null) add("deletes $key from the folder, which the index never saw")
                if (entry != null && remote[key] != entry.remoteRevision) add("deletes $key from the folder, changed since the last run")
            }
            val deletedPictures = (deletedHere + deletedThere).filter { it.kind == LibraryCollections.FILES }
            deletedPictures.forEach { key ->
                if (named == null) add("deletes $key while a song here could not be read")
                if (named != null && key.name in named) add("deletes $key, which a song here names")
            }
            // A song coming down or going may name a picture this device cannot see named yet, or keep one by an edit.
            if (deletedPictures.isNotEmpty() && operations.any { it.key.kind == LibraryCollections.SONGS && it.isSongSetChange }) {
                add("deletes a picture in a pass that changes which songs there are")
            }
            // A picture's version is its name: two of one size are two versions, though the index knows both by it.
            val versionsDeletedHere = deletedHere.mapTo(mutableSetOf()) { it.kind to if (it.kind == LibraryCollections.FILES) it.name else local[it] }
            val versionsDeletedThere = deletedThere.mapTo(mutableSetOf()) { it.kind to if (it.kind == LibraryCollections.FILES) it.name else index[it]?.localHash }
            (versionsDeletedHere intersect versionsDeletedThere).forEach { add("deletes the version ${it.second} on both sides") }
            (local.keys + remote.keys).forEach { key ->
                val entry = index[key]
                val hash = local[key]
                val revision = remote[key]
                if (hash != null && revision == null && hash != entry?.localHash && operations.none { it is SyncOperation.Upload && it.key == key }) {
                    add("leaves $key missing from the folder, though it changed here")
                }
                if (revision != null && hash == null && revision != entry?.remoteRevision && operations.none { it is SyncOperation.Download && it.key == key }) {
                    add("leaves $key missing here, though it changed in the folder")
                }
            }
        }

        private val SyncOperation.isSongSetChange get() = this !is SyncOperation.Upload && this !is SyncOperation.Forget
    }

    private companion object {
        const val MAXIMUM_REPORTED = 20

        /** A second content shares the first's hashes and revisions or not: one song held under two names, or two songs. */
        val CONTENT = listOf(Content("a", "a+", "r1"), Content("b", "b+", "r2"))
        val PICTURE_CONTENT = listOf(
            Content(localImageHash(10), localImageHash(11), "p1"),
            Content(localImageHash(20), localImageHash(21), "p2"),
        )

        /**
         * Each document with its other names: its name in another case and decomposed, which the provider
         * takes for the same file, and another name altogether, which a rename leaves (the planner knows no moves).
         */
        val DOCUMENTS = listOf(
            SyncKey(LibraryCollections.SONGS, "Café - Title.cho") to listOf("café - title.cho", "Café - Title.cho", "Café - Old title.cho"),
            SyncKey(LibraryCollections.SETLISTS, "Café night.setlist.json") to
                listOf("café NIGHT.setlist.json", "Café night.setlist.json", "Old night.setlist.json"),
        ).map { (key, names) -> key to names.map { key.copy(name = it) } }

        val NAMING_SONG = SyncKey(LibraryCollections.SONGS, "naming.cho")
        val OTHER_SONG = SyncKey(LibraryCollections.SONGS, "other.cho")
        val FIRST_PICTURE = SyncKey(LibraryCollections.FILES, "0123456789abcdef.png")
        val SECOND_PICTURE = SyncKey(LibraryCollections.FILES, "fedcba9876543210.jpg")
    }
}
