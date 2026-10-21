/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.recovering
import com.pandulapeter.campfire.data.source.local.api.SharedLinksLocalSource
import com.pandulapeter.campfire.data.sync.implementation.SyncEnvironment
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 * `preferences/shared-links-journal.json`: renames and deletions of shared items made on this device, in order, that
 * the account's list has not been told yet. After the fact a rename looks like a deletion plus a new file, so only the
 * device that made one can say which it was; the next run applies what is here before it looks items up, and removes
 * what it applied. Deleted with the sync index when the account goes.
 */
@Single
internal class SharedLinksJournal(
    private val localSource: SharedLinksLocalSource,
    private val environment: SyncEnvironment,
) {

    @Serializable
    sealed interface Entry {

        /** The shared item [from] is now the library file [to]; its link stays. */
        @Serializable
        @SerialName("rename")
        data class Rename(
            val from: String,
            val to: String,
        ) : Entry

        /** The shared item was deleted in Campfire, which stops sharing it. */
        @Serializable
        @SerialName("stop")
        data class Stop(
            val shareId: String,
        ) : Entry
    }

    @Serializable
    private data class Document(
        val entries: List<Entry> = emptyList(),
    )

    private val mutex = Mutex()

    /**
     * Throws where the file is there and cannot be read. One that reads but does not decode holds nothing anyone can
     * apply, and counts as empty.
     */
    suspend fun load() = mutex.withLock { read() }

    suspend fun append(entry: Entry) = mutex.withLock { write(read() + entry) }

    /** Removes [applied], each once, keeping whatever was appended while they were being applied. */
    suspend fun remove(applied: List<Entry>) = mutex.withLock {
        val entries = read().toMutableList()
        applied.forEach { entries.remove(it) }
        write(entries)
    }

    suspend fun clear() = mutex.withLock { localSource.saveSharedLinksJournal(null) }

    private suspend fun read(): List<Entry> {
        val text = localSource.loadSharedLinksJournal() ?: return emptyList()
        return environment.logger.recovering(
            describe = { "Could not decode the shared links journal: ${it::class.simpleName}" },
            fallback = { emptyList() },
        ) { json.decodeFromString<Document>(text).entries }
    }

    private suspend fun write(entries: List<Entry>) =
        localSource.saveSharedLinksJournal(if (entries.isEmpty()) null else json.encodeToString(Document(entries)))

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}
