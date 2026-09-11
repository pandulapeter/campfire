/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pandulapeter.campfire.CampfireActivity
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.domain.api.useCases.CancelSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncStateUseCase
import com.pandulapeter.campfire.presentation.ui.platform.withSyncCounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Keeps the process alive for as long as a sync run lasts, and shows the run as a notification.
 *
 * Without it, a run would end the moment the user left the app: Android stops a process nobody can see, and a
 * library of a few hundred songs takes longer than the few seconds that buys. The service does no syncing of its
 * own - the run lives in `SyncRepository`, which is a singleton and therefore outlives every screen - it only tells
 * Android that something worth keeping alive is going on, and tells the user the same thing.
 *
 * Every string is handed over in the intent rather than read from resources here, so that the notification follows
 * the language chosen inside the app, which may differ from the system's. They are kept, rather than used once,
 * because the activity is the first thing to go when the user swipes the app away and the run carries on without
 * it: from that point this service is the only thing left that can move the notification along, so it renders the
 * text itself from the words it was given and the state it watches.
 */
class CampfireSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var latestSyncState: SyncState = SyncState.Disconnected
    private var isInForeground = false
    private var words: Words? = null
    private var completed = 0
    private var total = 0

    /** The translated words of one run, kept for as long as it lasts. See the note on the class. */
    private data class Words(
        val channelName: String,
        val title: String,
        val preparingBody: String,
        val progressBodyFormat: String,
        val stopLabel: String,
    )

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * The end of the run is watched from here as well as reported by the activity: the activity that started this
     * service is the very thing that may be gone by the time the run ends, and nothing else would take the
     * notification down again. It would say "syncing" for as long as the process lived.
     */
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            KoinPlatform.getKoin().get<GetSyncStateUseCase>().invoke().collect { state ->
                latestSyncState = state
                (state as? SyncState.Connected)?.progress?.let { progress ->
                    completed = progress.completed
                    total = progress.total
                }
                // The counts come from here as well as from the activity, and the two never disagree: while the
                // activity is alive both say the same thing, and once it is gone this is the only one still talking.
                if (!stopIfNothingIsRunning()) {
                    updateNotification()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // Only the notification's own button means "stop the run". The app saying there is nothing left to show
            // means the run is already over, and must never be able to cancel anything - least of all the next run,
            // which is what it did when this arrived just after the user had started one.
            ACTION_STOP -> {
                KoinPlatform.getKoin().get<CancelSynchronizationUseCase>().invoke()
                stop()
            }

            ACTION_DISMISS -> stop()

            else -> {
                words = intent?.toWords() ?: return START_NOT_STICKY
                completed = intent.getIntExtra(EXTRA_COMPLETED, completed)
                total = intent.getIntExtra(EXTRA_TOTAL, total)
                val notification = buildNotification() ?: return START_NOT_STICKY
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
                )
                isInForeground = true
                // The run may have ended between the intent being sent and its arrival here.
                stopIfNothingIsRunning()
            }
        }
        // Not sticky: a run that the system killed the process of is over, and restarting the service without the
        // app around it would show a notification for something that is not happening.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Only once the service is in the foreground: stopping before then would leave the system waiting for the
     * `startForeground` that `startForegroundService` promised it, which it treats as a crash of the app.
     */
    private fun stopIfNothingIsRunning(): Boolean {
        if (isInForeground && (latestSyncState as? SyncState.Connected)?.progress == null) {
            stop()
            return true
        }
        return false
    }

    /**
     * Re-posting under the same id is how a foreground service's notification is updated. Only once it is in the
     * foreground: before that there is nothing to update, and posting anyway would put up a second, ordinary
     * notification that no `stopForeground` would ever take down again.
     */
    private fun updateNotification() {
        if (!isInForeground) return
        val notification = buildNotification() ?: return
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
    }

    private fun stop() {
        isInForeground = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun Intent.toWords() = Words(
        channelName = getStringExtra(EXTRA_CHANNEL_NAME).orEmpty(),
        title = getStringExtra(EXTRA_TITLE).orEmpty(),
        preparingBody = getStringExtra(EXTRA_PREPARING_BODY).orEmpty(),
        progressBodyFormat = getStringExtra(EXTRA_PROGRESS_BODY_FORMAT).orEmpty(),
        stopLabel = getStringExtra(EXTRA_STOP_LABEL).orEmpty(),
    )

    private fun buildNotification(): Notification? {
        val words = words ?: return null
        // Indeterminate until both sides have been listed, which is also when there is nothing to count yet.
        val body = if (total == 0) {
            words.preparingBody
        } else {
            words.progressBodyFormat.withSyncCounts(completed, total)
        }
        createChannel(words.channelName)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setContentTitle(words.title)
            .setContentText(body)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, CampfireActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    words.stopLabel,
                    PendingIntent.getService(
                        this,
                        1,
                        Intent(this, CampfireSyncService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build()
            )
            .setProgress(total, completed, total == 0)
            .build()
    }

    /** Low importance: this is something to be able to look at, not something to be interrupted by. */
    private fun createChannel(channelName: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "sync"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.pandulapeter.campfire.action.STOP_SYNC"
        private const val ACTION_DISMISS = "com.pandulapeter.campfire.action.DISMISS_SYNC"
        private const val EXTRA_CHANNEL_NAME = "channelName"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PREPARING_BODY = "preparingBody"
        private const val EXTRA_PROGRESS_BODY_FORMAT = "progressBodyFormat"
        private const val EXTRA_STOP_LABEL = "stopLabel"
        private const val EXTRA_COMPLETED = "completed"
        private const val EXTRA_TOTAL = "total"

        fun intent(
            context: Context,
            channelName: String,
            title: String,
            preparingBody: String,
            progressBodyFormat: String,
            stopLabel: String,
            completed: Int,
            total: Int,
        ) = Intent(context, CampfireSyncService::class.java)
            .putExtra(EXTRA_CHANNEL_NAME, channelName)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_PREPARING_BODY, preparingBody)
            .putExtra(EXTRA_PROGRESS_BODY_FORMAT, progressBodyFormat)
            .putExtra(EXTRA_STOP_LABEL, stopLabel)
            .putExtra(EXTRA_COMPLETED, completed)
            .putExtra(EXTRA_TOTAL, total)

        /** Takes the notification down because the run is over. Never cancels anything - see onStartCommand. */
        fun dismissIntent(context: Context) = Intent(context, CampfireSyncService::class.java).setAction(ACTION_DISMISS)

        /**
         * Takes down a notification left behind by a run whose process never came back.
         *
         * The service's own watch on the sync state removes it whenever a run ends while the process is alive, but
         * a process that is killed mid run - swiped away on a device that does not spare it, or shut down under
         * memory pressure - takes that watch with it, and START_NOT_STICKY rightly declines to restart the service
         * just to tidy up. So the next launch does it instead, which is where this is called from. Safe by
         * construction: an application is only ever created once per process, so there is never a live run of this
         * app's own whose notification this could pull out from under it.
         */
        fun clearStaleNotification(context: Context) =
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }
}
