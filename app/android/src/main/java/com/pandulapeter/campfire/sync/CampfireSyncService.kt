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
 * the language chosen inside the app, which may differ from the system's.
 */
class CampfireSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var latestSyncState: SyncState = SyncState.Disconnected
    private var isInForeground = false

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
                stopIfNothingIsRunning()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                KoinPlatform.getKoin().get<CancelSynchronizationUseCase>().invoke()
                stop()
            }

            else -> {
                val notification = intent?.let(::buildNotification) ?: return START_NOT_STICKY
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
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
    private fun stopIfNothingIsRunning() {
        if (isInForeground && (latestSyncState as? SyncState.Connected)?.progress == null) {
            stop()
        }
    }

    private fun stop() {
        isInForeground = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(intent: Intent): Notification {
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val stopLabel = intent.getStringExtra(EXTRA_STOP_LABEL).orEmpty()
        val completed = intent.getIntExtra(EXTRA_COMPLETED, 0)
        val total = intent.getIntExtra(EXTRA_TOTAL, 0)
        createChannel(channelName)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, CampfireActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    stopLabel,
                    PendingIntent.getService(
                        this,
                        1,
                        Intent(this, CampfireSyncService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )
            // Indeterminate until both sides have been listed, since until then there is nothing to count.
            .setProgress(total, completed, total == 0)
            .build()
    }

    /** Low importance: this is something to be able to look at, not something to be interrupted by. */
    private fun createChannel(channelName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
        private const val EXTRA_CHANNEL_NAME = "channelName"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_STOP_LABEL = "stopLabel"
        private const val EXTRA_COMPLETED = "completed"
        private const val EXTRA_TOTAL = "total"

        fun intent(
            context: Context,
            channelName: String,
            title: String,
            body: String,
            stopLabel: String,
            completed: Int,
            total: Int
        ) = Intent(context, CampfireSyncService::class.java)
            .putExtra(EXTRA_CHANNEL_NAME, channelName)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_BODY, body)
            .putExtra(EXTRA_STOP_LABEL, stopLabel)
            .putExtra(EXTRA_COMPLETED, completed)
            .putExtra(EXTRA_TOTAL, total)

        fun stopIntent(context: Context) = Intent(context, CampfireSyncService::class.java).setAction(ACTION_STOP)
    }
}
