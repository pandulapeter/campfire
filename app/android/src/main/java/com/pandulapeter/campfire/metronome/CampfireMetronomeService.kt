/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.pandulapeter.campfire.CampfireMainActivity
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Keeps the process alive while a click plays, and is the click's face to the system: a media session, which the lock
 * screen, the headset's button and Bluetooth controls talk to, and its media notification with Stop.
 *
 * Like the sync service it plays nothing itself - the engine is the `Metronome` singleton, whose audio output also owns
 * the audio focus and the headphones being pulled - and it follows the engine's own state for stopping rather than
 * being told, since the activity that started it may be gone by the time the click ends. There is no paused state:
 * the session's pause, stop and the headset's button all stop the click, which is what ends the session too.
 *
 * Started by the activity the moment a click starts, which is always a tap with the app in front, so the background
 * start restrictions never apply; the words arrive in the intent so that the notification is in the language chosen
 * in the app. A media session's notification needs no notification permission.
 */
class CampfireMetronomeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val metronome by lazy { KoinPlatform.getKoin().get<Metronome>() }
    private var session: MediaSession? = null
    private var isInForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, SESSION_TAG).apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPause() = metronome.stop()

                override fun onStop() = metronome.stop()
            })
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_STOP or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                    .build()
            )
            isActive = true
        }
        scope.launch {
            // The state of the moment is skipped: the intent that started this service was sent because a click started,
            // and a stop that came before it arrived is answered in onStartCommand.
            metronome.playback.drop(1).collect { if (it !is MetronomePlayback.Playing) stop() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            metronome.stop()
            stop()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent?.getStringExtra(EXTRA_BODY).orEmpty()
        val notification = buildNotification(
            channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty(),
            title = title,
            body = body,
            stopLabel = intent?.getStringExtra(EXTRA_STOP_LABEL).orEmpty(),
        )
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, body)
                .build()
        )
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        isInForeground = true
        isRunning = true
        // The click may have stopped between the intent being sent and its arrival here; the service has to have gone
        // into the foreground first all the same, or the system takes the promise startForegroundService made for a
        // crash.
        if (metronome.playback.value !is MetronomePlayback.Playing) stop()
        // Not sticky: a click whose process the system killed is over, and a session for it would be a lie.
        return START_NOT_STICKY
    }

    /**
     * Swiping the app away is a request for silence, and this is where it arrives: the manifest leaves stopWithTask off,
     * since a service stopped with its task is never told, and the engine would play on in a process nobody can see.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        metronome.stop()
        stop()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        session?.release()
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private fun stop() {
        if (isInForeground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isInForeground = false
        isRunning = false
        stopSelf()
    }

    private fun buildNotification(channelName: String, title: String, body: String, stopLabel: String): Notification {
        createChannel(channelName)
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CampfireMetronomeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_metronome)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            // Brings the task that is there to the front rather than starting the app again, which would not stop the
            // click either way: the engine keeps where it was started (its origin), and a fresh screen reads it.
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, CampfireMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .addAction(Notification.Action.Builder(null, stopLabel, stopIntent).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0))
            .build()
    }

    /** Low importance: a playing click is something to be able to reach, not something to be interrupted by. */
    private fun createChannel(channelName: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    companion object {
        /**
         * Whether the service is in the foreground, for the activity to update its words with a plain start rather than
         * a foreground one: an app with a foreground service may start it from anywhere, while a foreground start from
         * the background is refused. Process-wide, like the service.
         */
        @Volatile
        var isRunning = false
            private set

        private const val SESSION_TAG = "Metronome"
        private const val CHANNEL_ID = "metronome"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_STOP = "com.pandulapeter.campfire.action.STOP_METRONOME"
        private const val EXTRA_CHANNEL_NAME = "channelName"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_STOP_LABEL = "stopLabel"

        fun intent(
            context: Context,
            channelName: String,
            title: String,
            body: String,
            stopLabel: String,
        ) = Intent(context, CampfireMetronomeService::class.java)
            .putExtra(EXTRA_CHANNEL_NAME, channelName)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_BODY, body)
            .putExtra(EXTRA_STOP_LABEL, stopLabel)
    }
}
