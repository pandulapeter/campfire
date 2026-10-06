/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single

@Single
internal class SetlistRepositoryImpl(
    private val setlistLocalSource: SetlistLocalSource,
    private val libraryFileLock: LibraryFileLock,
    private val libraryChanges: LibraryChanges,
) : BaseLocalDataRepository<List<Setlist>>(), SetlistRepository {

    override val setlists = dataState

    /**
     * Held from reading a setlist to having its write in the cache, so that the next change reads what this one
     * wrote. The cache is the one place that is right straight after a write: anything observing [setlists] only
     * catches up a few hops later, and a file write is plenty of time for a second tap to land in between. Every
     * write to a setlist file takes it, the ones that read nothing included, since a save, a move or a deletion
     * crossing a change that is halfway through is how a setlist ends up holding the older of the two, twice in the
     * library, or back after it was deleted. It also covers the creations and the imports, from the storage finding a
     * name free to the file being there under it, so that two of them cannot be given the same one.
     *
     * The lock is waited for cancellably, so a change that has not started yet goes away with its screen, but what
     * runs under it is one [NonCancellable] step ([writing]): the file and the cache change together or not at all.
     * The repository outlives every screen, and a write that reached the disk with its caller cancelled on the way
     * back would leave the cache on the old version, which the next change then builds on and writes over the file.
     *
     * [libraryFileLock] is taken inside it for the same span, since sync writes setlist files too: a download landing
     * between a change reading the file and writing it back would be written over by the version from before it.
     */
    private val writeMutex = Mutex()

    /** The setlists the last reads found with no day of their own, which [writeDays] saves with the one they were given. */
    private val undatedFileNames = MutableStateFlow(emptySet<String>())

    /**
     * Held by [writeDays] from taking the names to its last write, so that a second caller waits for those writes rather
     * than finding nothing left to do: the launch's sync run waits for the first read this way, and then sees the days.
     */
    private val dayWriteMutex = Mutex()

    /**
     * Writes nothing, since it runs under the base read lock that [latest] takes while it holds [writing]'s two: the
     * days are written after it by [writeDays].
     */
    override suspend fun loadDataFromLocalSource() = setlistLocalSource.loadSetlists().let { parsed ->
        val undated = parsed.filterNot { it.isDated }.map { it.setlist.fileName }
        if (undated.isNotEmpty()) undatedFileNames.update { it + undated }
        parsed.map { it.setlist }
    }

    override suspend fun loadSetlistsIfNeeded() = loadDataIfNeeded().also { writeDays() }

    /**
     * Read outside [writing] on purpose: [updateSetlist] reads each file again under both locks before it changes it, so
     * this list only decides which files to ask, and [libraryFileLock] is not reentrant.
     */
    override suspend fun loadSetlistFileNamesNaming(songFileName: String): List<String> {
        fun Setlist.names() = entries.any { it.songFileName == songFileName }
        val onDisk = setlistLocalSource.loadSetlists().map { it.setlist }.filter { it.names() }.map { it.fileName }
        val cached = loadDataIfNeeded().orEmpty().filter { it.names() }.map { it.fileName }
        return (onDisk + cached).distinct()
    }

    override suspend fun rescan() {
        reloadData()
        writeDays()
    }

    override suspend fun refresh(fileNames: Set<String>) {
        if (fileNames.isEmpty()) return
        if (setlists.first().data == null) return rescan()
        // Under the lock every change holds from its write to its cache update, so that a change lands either before
        // these reads, which then see it, or after the list has been updated with them - never between, where the version
        // read here would be put back over it. The lock alone, not writeMutex, which [writing] takes before it.
        libraryFileLock.withLock {
            val reloaded = fileNames.mapNotNull { fileName ->
                try {
                    setlistLocalSource.loadSetlist(fileName)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    println("Could not read the setlist \"$fileName\": ${exception.message}")
                    null
                }
            }
            updateData { current -> current.orEmpty().filterNot { it.fileName in fileNames } + reloaded }
        }
    }

    override suspend fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist = writing {
        setlistLocalSource.createSetlist(title = title, description = description, date = date, isCountdownShown = isCountdownShown).also { created ->
            updateData { current -> current.orEmpty().filterNot { it.fileName == created.fileName } + created }
            libraryChanges.onLibraryChanged()
        }
    }

    override suspend fun saveSetlist(setlist: Setlist) {
        writing { write(setlist) }
    }

    override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist) = writing {
        latest(fileName)?.let(transform)?.let { write(it) }
    }

    override suspend fun renameSetlist(fileName: String, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = writing {
        latest(fileName)?.let { setlist ->
            val edited = setlist.copy(description = description, date = date, isCountdownShown = isCountdownShown)
            setlistLocalSource.renameSetlist(setlist = edited, title = title).also { renamed ->
                updateData { current ->
                    current.orEmpty().filterNot { it.fileName == fileName || it.fileName == renamed.fileName } + renamed
                }
                libraryChanges.onLibraryChanged()
            }
        }
    }

    override suspend fun parseSetlist(document: String) = setlistLocalSource.parseSetlist(document)

    override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean) = writing {
        setlistLocalSource.importSetlist(setlist, shouldReplace).also { libraryChanges.onLibraryChanged() }
    }

    override suspend fun adoptImported(setlists: Collection<Setlist>) {
        if (setlists.isEmpty()) return
        if (this.setlists.first().data == null) return rescan()
        val fileNames = setlists.mapTo(hashSetOf()) { it.fileName }
        // The last write of a name is the file, should an import ever write one twice.
        updateData { current -> current.orEmpty().filterNot { it.fileName in fileNames } + setlists.associateBy { it.fileName }.values }
    }

    override suspend fun loadSetlistFileSizes() = setlistLocalSource.loadSetlistFileSizes()

    override suspend fun loadSetlistDocument(fileName: String, songFileNames: Set<String>?) =
        setlistLocalSource.loadSetlistDocument(fileName, songFileNames)

    override suspend fun deleteSetlist(fileName: String) = writing {
        setlistLocalSource.deleteSetlist(fileName)
        forget(fileName)
        libraryChanges.onLibraryChanged()
    }

    override suspend fun deleteAllSetlists() = writing {
        val remaining = deleteEach(setlistLocalSource.loadSetlistFileSizes().keys, setlistLocalSource::deleteSetlist)
        updateData { current -> current.orEmpty().filter { it.fileName in remaining } }
        libraryChanges.onLibraryChanged()
        remaining.throwFirstFailure()
    }

    /**
     * Runs [block] under [writeMutex] and then [libraryFileLock], taken cancellably in that order and held until the
     * block has finished whatever happens.
     */
    private suspend fun <T> writing(block: suspend () -> T): T = writeMutex.withLock {
        libraryFileLock.withLock { withContext(NonCancellable) { block() } }
    }

    /**
     * The setlist as its file holds it. Sync writes setlist files without going through this repository, and the cache
     * only catches up at the next rescan: a change built on the cache in between would put the version from before the
     * run back, and the next run would upload it over the edit it had just brought in. A file that cannot be decoded
     * (edited by hand into invalid JSON) falls back on the cache, which is what every change was built on before.
     * Callers hold [writeMutex], so no write of this repository can be halfway to the file.
     */
    private suspend fun latest(fileName: String): Setlist? {
        val cached = loadDataIfNeeded()?.firstOrNull { it.fileName == fileName }
        return try {
            setlistLocalSource.loadSetlist(fileName).also { if (it == null && cached != null) forget(fileName) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not read the setlist \"$fileName\": ${exception.message}")
            cached
        }
    }

    /**
     * Saves every setlist a read found undated with the day it was given, from a fresh read under both locks like any
     * other change: the bulk read is minutes old by the end of a large library, and a sync download or an edit of the
     * file landing since is what would be written over. [latest] reads it again and dates *that* content, or leaves a
     * file that got a day meanwhile alone. Announces nothing, so it starts no sync run; the next one carries the day.
     */
    private suspend fun writeDays() = dayWriteMutex.withLock {
        undatedFileNames.getAndUpdate { emptySet() }.forEach { fileName ->
            writing { latest(fileName)?.let { dated -> updateData { current -> current.orEmpty().filterNot { it.fileName == dated.fileName } + dated } } }
        }
    }

    private fun forget(fileName: String) = updateData { current -> current.orEmpty().filterNot { it.fileName == fileName } }

    /** Callers hold [writeMutex]. Returns the setlist as it was written, carrying its file's size. */
    private suspend fun write(setlist: Setlist): Setlist {
        val saved = setlistLocalSource.saveSetlist(setlist)
        updateData { current -> current.orEmpty().filterNot { it.fileName == saved.fileName } + saved }
        libraryChanges.onLibraryChanged()
        return saved
    }
}
