/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.measureTime

/**
 * Sync writes song and setlist files behind these two repositories' backs, so it is the one thing that has to tell
 * them to read the library again. It used to be the use case's job, back when a run finished before the caller did;
 * now that a run outlives whoever started it, this is the only place that reliably still exists when it ends.
 *
 * Its throttle is not per run: the pause a live refresh earned carries over into the next run, as it always has.
 */
@Single
internal class SyncLibraryRefresher(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val environment: SyncEnvironment,
) {

    /** Nothing launched here may close the app, see [SyncEnvironment.scopeFor]. */
    private val scope = environment.scopeFor("sync")

    private var liveRefreshJob: Job? = null

    /** The library files the running run has changed and the repositories have not read again yet. */
    private val changedFiles = mutableSetOf<SyncKey>()
    private val changedFilesMutex = Mutex()

    /** When the last live refresh ended and how long the next one has to wait for, see [scheduleLiveRefresh]. */
    @Volatile
    private var lastLiveRescanEnd: TimeMark? = null

    @Volatile
    private var liveRescanPause = LIVE_RESCAN_INTERVAL

    /**
     * A launch starts a run beside the first scan of the library, and both read every file, so the screen's read goes
     * first. Waited for rather than started again: a repository that is not Loading has been read, or has failed,
     * which a second read here would only repeat.
     */
    suspend fun awaitFirstRead() {
        if (songRepository.songs.first() is DataState.Loading) songRepository.loadSongsIfNeeded()
        if (setlistRepository.setlists.first() is DataState.Loading) setlistRepository.loadSetlistsIfNeeded()
    }

    /** Notes a library file the running run has changed, for the next refresh to read. */
    suspend fun onFileChanged(key: SyncKey) {
        changedFilesMutex.withLock { changedFiles += key }
    }

    /**
     * Keeps the library counts moving while a run is going.
     *
     * Sync writes files behind the two repositories' backs, so nothing reads them again until something says to: this
     * hands them the files the run has changed so far, which they read again one by one. Throttled by what the last one
     * cost, see [liveRescanPauseAfter], since a first sync changes files several times a second and every refresh
     * rebuilds the lists downstream. Whatever is still waiting when the run ends is refreshed then.
     */
    fun scheduleLiveRefresh() {
        if (liveRefreshJob?.isActive == true) return
        if (lastLiveRescanEnd?.let { it.elapsedNow() < liveRescanPause } == true) return
        liveRefreshJob = scope.launch {
            val duration = environment.timeSource.measureTime { refreshChangedFiles() }
            liveRescanPause = liveRescanPauseAfter(duration)
            lastLiveRescanEnd = environment.timeSource.markNow()
        }
    }

    suspend fun rescanLibrary() {
        songRepository.rescan()
        setlistRepository.rescan()
    }

    /**
     * Hands the files changed since the last refresh to the two repositories. They are taken out of [changedFiles]
     * before they are read, so a file the run changes again meanwhile waits for the next refresh, and put back when
     * this one is stopped before it got to them.
     */
    private suspend fun refreshChangedFiles() {
        val keys = changedFilesMutex.withLock { changedFiles.toSet().also { changedFiles.clear() } }
        if (keys.isEmpty()) return
        try {
            songRepository.refresh(keys.namesOf(LibraryFileKind.SONG))
            setlistRepository.refresh(keys.namesOf(LibraryFileKind.SETLIST))
        } catch (exception: CancellationException) {
            withContext(NonCancellable) { changedFilesMutex.withLock { changedFiles += keys } }
            throw exception
        }
    }

    private fun Set<SyncKey>.namesOf(kind: LibraryFileKind) = filter { it.kind == kind }.mapTo(mutableSetOf()) { it.name }

    /**
     * The refresh a run ends with. A live one still reading is stopped first, which puts back what it had not got to,
     * and everything still waiting is read here.
     */
    suspend fun refreshAfterRun() {
        liveRefreshJob?.cancelAndJoin()
        refreshChangedFiles()
    }
}

/**
 * How long the counters wait after a live refresh that took [duration]. A refresh reads every file changed since the
 * last one, which on a first sync of a large library is a lot of them, and each one rebuilds the lists downstream:
 * waiting a multiple of what it cost caps the share of a run that goes into re-reading what it has already written,
 * while a small run keeps the interval that makes the numbers visibly move.
 */
internal fun liveRescanPauseAfter(duration: Duration) = maxOf(LIVE_RESCAN_INTERVAL, duration * LIVE_RESCAN_PAUSE_FACTOR)

/** Often enough that the counters visibly move, where the reading costs next to nothing. */
internal val LIVE_RESCAN_INTERVAL = 1.seconds

/** One part reading to five parts not: a live refresh never takes more than about a sixth of a run. */
private const val LIVE_RESCAN_PAUSE_FACTOR = 5
