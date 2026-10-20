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
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesDocument
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
 * run that follows a change hands over a new point to watch from.
 */
@Single
internal class SyncRemoteWatcher(
    private val runner: SyncRunner,
    private val scheduler: SyncRunScheduler,
    private val connectivityMonitor: ConnectivityMonitor,
    private val environment: SyncEnvironment,
) {

    /** Watches until cancelled, which the caller does as the app leaves the front. */
    suspend fun watch() {
        runner.watchPoint.collectLatest { point -> if (point != null) watchFrom(point) }
    }

    private suspend fun watchFrom(point: SyncWatchPoint) {
        var cursor = point.cursor
        var failures = 0
        while (true) {
            try {
                if (!point.folder.awaitChanges(cursor)) {
                    failures = 0
                    continue
                }
                val changes = point.folder.changesSince(cursor)
                val isOtherDevices = point.hasOtherChangesThanPreferences(changes) ||
                    SyncedPreferencesDocument.FILE_NAME in changes.documents &&
                    point.isOtherPreferences(point.folder.downloadDocument(SyncedPreferencesDocument.FILE_NAME)?.bytes)
                // Moved on, and the waits started over, only once the changes are judged: a preferences download that
                // failed asks again about the same changes, after waits that keep growing while it fails.
                cursor = changes.cursor
                failures = 0
                if (isOtherDevices) {
                    scheduler.schedule()
                    return
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: SyncNetworkException) {
                // Offline, or Dropbox busy: the same cursor is asked about again once the wait is over, or as soon as the
                // device is back online, and nothing that changed meanwhile is lost by waiting.
                connectivityMonitor.awaitReconnection(NETWORK_RETRY_DELAYS[minOf(failures, NETWORK_RETRY_DELAYS.lastIndex)])
                failures++
            } catch (exception: Exception) {
                // A cursor the service no longer takes, or an answer nothing here expected: only a run's fresh listing
                // gives a cursor to go on from, and a run is also what settles whatever did change.
                environment.logger.log("Stopped watching the sync folder: ${exception.message}")
                scheduler.schedule()
                return
            }
        }
    }

    private companion object {
        /** The waits after the feed could not be reached, the last one repeated. */
        val NETWORK_RETRY_DELAYS: List<Duration> = listOf(30.seconds, 1.minutes, 2.minutes, 5.minutes)
    }
}
