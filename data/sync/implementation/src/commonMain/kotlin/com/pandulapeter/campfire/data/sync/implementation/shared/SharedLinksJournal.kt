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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
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
 *
 * A build older than the [Entry.StopItem] and [Entry.StopAll] entries reads a journal holding one as empty, and forgets
 * what it held: downgrades are not supported.
 */
@Single
internal class SharedLinksJournal(
    private val localSource: SharedLinksLocalSource,
    private val environment: SyncEnvironment,
) {

    @Serializable
    sealed interface Entry {

        /**
         * The shared item [from] is now the library file [to]; its link stays. [shareId] names the share where this
         * device knew it, so that a new share of another item under the old name is not moved too; without it (an item
         * the mirror did not name, or a journal written before it) every entry of that name is.
         */
        @Serializable
        @SerialName("rename")
        data class Rename(
            val from: String,
            val to: String,
            val shareId: String? = null,
        ) : Entry

        /** The shared item was deleted in Campfire, which stops sharing it. */
        @Serializable
        @SerialName("stop")
        data class Stop(
            val shareId: String,
        ) : Entry

        /**
         * The library file [fileName] of [kind] (a `SharedLinkKind` id, as the list stores it) was deleted in Campfire;
         * whatever share of it the list names when this is applied stops. Noted where the mirror did not name one, since
         * another device may have shared the item since this one last read the list.
         */
        @Serializable
        @SerialName("stopItem")
        data class StopItem(
            val kind: String,
            val fileName: String,
        ) : Entry

        /** The whole library was deleted in Campfire; every share the list names when this is applied stops. */
        @Serializable
        @SerialName("stopAll")
        data object StopAll : Entry
    }

    @Serializable
    private data class Document(
        val entries: List<Entry> = emptyList(),
    )

    private val mutex = Mutex()

    /** Held from loading the entries to apply until they are removed, see [whileApplying]. */
    private val applicationMutex = Mutex()

    /** The entries as last read or written; null until read from the disk. */
    private val state = MutableStateFlow<List<Entry>?>(null)

    /**
     * The entries, read from the disk the first time. A journal that cannot be read shows as empty here, and keeps
     * failing the calls that write it.
     */
    val entries = flow {
        try {
            load()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            emit(emptyList())
        }
        emitAll(state.filterNotNull())
    }.distinctUntilChanged()

    /**
     * Throws where the file is there and cannot be read. One that reads but does not decode holds nothing anyone can
     * apply, and counts as empty.
     */
    suspend fun load() = mutex.withLock { read() }

    suspend fun append(entries: List<Entry>) = mutex.withLock { write(read() + entries) }

    /**
     * Removes [applied], each once, keeping whatever was appended while they were being applied. Each is matched to
     * its first equal entry, which is the one applied: under [whileApplying], the entries applied are always the
     * journal's first ones.
     */
    suspend fun remove(applied: List<Entry>) = mutex.withLock {
        val entries = read().toMutableList()
        applied.forEach { entries.remove(it) }
        write(entries)
    }

    suspend fun clear() = mutex.withLock {
        localSource.saveSharedLinksJournal(null)
        state.value = emptyList()
    }

    /**
     * Runs [block], which loads entries, applies them, and removes them, while no other application does: two appliers
     * taking the same entries would each remove one, and the second would remove a later rename equal to it. Stop
     * sharing and Stop sharing everything hold it too, so that a run's shared-copy step never uploads a copy stopped
     * after it read the list. Separate from the file's lock, so that appending goes on meanwhile. Taken inside the run
     * lock, never around it.
     */
    suspend fun <T> whileApplying(block: suspend () -> T): T = applicationMutex.withLock { block() }

    private suspend fun read(): List<Entry> = state.value ?: readFile().also { state.value = it }

    private suspend fun readFile(): List<Entry> {
        val text = localSource.loadSharedLinksJournal() ?: return emptyList()
        return environment.logger.recovering(
            describe = { "Could not decode the shared links journal: ${it::class.simpleName}" },
            fallback = { emptyList() },
        ) { json.decodeFromString<Document>(text).entries }
    }

    private suspend fun write(entries: List<Entry>) {
        localSource.saveSharedLinksJournal(if (entries.isEmpty()) null else json.encodeToString(Document(entries)))
        state.value = entries
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}
