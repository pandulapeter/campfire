/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.scheduling

import com.pandulapeter.campfire.data.source.remote.api.ConnectivityMonitor
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.sync.implementation.SyncEnvironment
import com.pandulapeter.campfire.data.sync.implementation.downloadFolderDocument
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesDocument
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksFormat
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Brings another device's changes to this one while the app is in front: waits on the provider's feed of changes from
 * where the last completed run left the folder, and asks for an automatic run once something there is not this device's
 * own doing. Nothing is watched before a run has completed, nor while one is going (see [SyncRunner.watchPoint]): the
 * run that follows a change hands over a new point to watch from. A change of the shared links' list alone starts no run,
 * which nothing it settles needs, but reads the list again, so that a share made or stopped elsewhere reaches the screens.
 */
@Single
internal class SyncRemoteWatcher(
    private val runner: SyncRunner,
    private val scheduler: SyncRunScheduler,
    private val connectivityMonitor: ConnectivityMonitor,
    private val registry: SharedLinksRegistry,
    private val environment: SyncEnvironment,
) {

    /** Watches until cancelled, which the caller does as the app leaves the front. */
    suspend fun watch() {
        runner.watchPoint.collectLatest { point -> if (point != null) watchFrom(point) }
    }

    private suspend fun watchFrom(point: SyncWatchPoint) {
        var cursor = point.cursor
        var failures = 0
        environment.logger.log("Sync feed: watching")
        while (true) {
            try {
                val askedAt = environment.timeSource.markNow()
                val hasChanges = try {
                    point.folder.awaitChanges(cursor)
                } catch (exception: SyncNetworkException) {
                    // Held for a minute and then lost - the far end closing a quiet poll, the call's own bound, a
                    // connection that went silent, a middlebox at its idle limit - so the link carried it, and the next
                    // poll, asked at once with the same cursor, reports whatever came meanwhile. A poll can be held this
                    // long at most once a minute.
                    if (askedAt.elapsedNow() < HELD_POLL) throw exception
                    environment.logger.log("Sync feed: a poll held for ${askedAt.elapsedNow().inWholeSeconds}s was lost, asking again")
                    failures = 0
                    continue
                }
                if (!hasChanges) {
                    failures = 0
                    continue
                }
                val changes = point.folder.changesSince(cursor)
                // Counts and the folder's own document names only: a file's name would name a song.
                environment.logger.log(
                    "Sync feed: changes after ${askedAt.elapsedNow().inWholeSeconds}s (${changes.files.size} files, " +
                        "${changes.deletedFiles.size} deletions, documents: ${changes.documents.sorted().joinToString().ifEmpty { "none" }})",
                )
                val isOtherDevices = point.hasOtherChangesThanPreferences(changes) ||
                    SyncedPreferencesDocument.FILE_NAME in changes.documents &&
                    point.isOtherPreferences(point.folder.downloadFolderDocument(SyncedPreferencesDocument.FILE_NAME)?.bytes)
                // The run another device's change brings reads the list itself.
                if (!isOtherDevices && SharedLinksFormat.NAME in changes.documents) {
                    val read = registry.read(point.folder)
                    environment.logger.log(
                        "Sync feed: shared links read (" + when (read) {
                            is SharedLinksRegistry.Read.Readable -> "${read.document.links.size} links"
                            is SharedLinksRegistry.Read.FromNewerVersion -> "of a newer version"
                        } + ")",
                    )
                }
                // Moved on, and the waits started over, only once the changes are judged: a preferences download or a read
                // of the list that failed asks again about the same changes, after waits that keep growing while it fails.
                cursor = changes.cursor
                failures = 0
                if (isOtherDevices) {
                    scheduler.schedule(after = Duration.ZERO, reason = SyncRunScheduler.RunReason.OTHER_DEVICE)
                    return
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: SyncNetworkException) {
                // Offline, refused, or Dropbox busy: the same cursor is asked about again once the wait is over, or as soon as the
                // device is back online, and nothing that changed meanwhile is lost by waiting.
                environment.logger.log("Could not reach the sync folder's change feed: ${exception.message}")
                if (connectivityMonitor.awaitReconnection(NETWORK_RETRY_DELAYS[minOf(failures, NETWORK_RETRY_DELAYS.lastIndex)])) {
                    environment.logger.log("Sync feed: back online, polling again")
                }
                failures++
            } catch (exception: Exception) {
                // A cursor the service no longer takes, or an answer nothing here expected: only a run's fresh listing
                // gives a cursor to go on from, and a run is also what settles whatever did change.
                environment.logger.log("Stopped watching the sync folder: ${exception.message}")
                scheduler.schedule(after = Duration.ZERO, reason = SyncRunScheduler.RunReason.FEED_LOST)
                return
            }
        }
    }

    private companion object {
        /**
         * The waits after the feed could not be reached, the last one repeated. The first is short: a single failure on a
         * working link - a pooled connection the far end had already closed, one 5xx of Dropbox's - is the common one,
         * no reconnection is reported to end the wait for it, and the desktop reports none at all, so a long first wait
         * would leave another device's change unseen for that long.
         */
        val NETWORK_RETRY_DELAYS: List<Duration> = listOf(2.seconds, 30.seconds, 1.minutes, 2.minutes, 5.minutes)

        /**
         * How long a poll has to have been open for its failure to count as the service holding it and then losing it
         * rather than as the link failing: past the 20 s connect timeout of the engines that have one, well short of the
         * minutes a quiet folder's poll is held. Such a poll is asked again at once, which can repeat at most this often.
         */
        val HELD_POLL: Duration = 1.minutes
    }
}
