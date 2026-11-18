/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.importing

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
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pandulapeter.campfire.CampfireMainActivity
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.presentation.ui.platform.BackgroundNotificationScheduler
import com.pandulapeter.campfire.presentation.ui.platform.ImportNotification
import com.pandulapeter.campfire.presentation.ui.state.importing.IMPORT_NOTIFICATION_PROGRESS_STEPS
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportNotificationContent
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportWork
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportWorkState
import com.pandulapeter.campfire.presentation.ui.state.importing.contentOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the process alive for as long as an import downloads, unpacks, or writes once the user has left the app, and
 * shows how far it is, as [com.pandulapeter.campfire.sync.CampfireSyncService] does for a sync run, beside which it runs
 * with a notification of its own.
 *
 * Without it, Back from the root or a swipe from recents would end the import with the process: a swipe kills it at
 * once, and Back lets the cached-app freezer stop it within seconds. The service imports nothing - the work belongs to
 * [ImportWork], a singleton that outlives every screen - it only tells Android that something worth keeping alive is
 * going on. A content URI the import reads needs nothing from here: its stream is opened by the activity, and a stream
 * already open keeps reading after the activity's grant ends, for as long as the process lives.
 *
 * The words arrive in the intent, in the language chosen in the app, and are kept, since the activity may be gone long
 * before the import ends; the text is rendered here from them and from the work's state, which this service watches.
 */
class CampfireImportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val work by lazy { KoinPlatform.getKoin().get<ImportWork>() }
    private var isInForeground = false
    private var words: ImportNotification? = null

    /** The last state with something to show, which the notification keeps saying through the grace after the work. */
    private var shownState = ImportWorkState.IDLE

    /** What the notification posted last shows, so that a post changing nothing is not made. */
    private var postedContent: ImportNotificationContent? = null

    /**
     * Once a second, not sync's twice: the unpacking counts bytes several times a second, and every post rebuilds the
     * notification, during which a tap on its Cancel can be lost.
     */
    private val scheduler = BackgroundNotificationScheduler(
        scope = scope,
        updateInterval = 1.seconds,
        handoverGrace = 2.seconds,
        postUpdate = ::updateNotification,
        release = ::stop,
        isRunGoing = { work.state.value != ImportWorkState.IDLE },
    )

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * The work's end is watched from here rather than reported by the activity, which may be gone by then. A change of
     * phase is posted at once, since it changes the notification's shape (its Cancel action comes or goes); the bytes
     * within a phase at the scheduler's pace, and only where what the notification shows changes.
     */
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            combine(work.state, work.currentProgress) { state, _ -> state }.collect { state ->
                if (state == ImportWorkState.IDLE) {
                    scheduleStop()
                } else {
                    scheduler.cancelRelease()
                    val isNewPhase = state != shownState
                    shownState = state
                    if (isInForeground) scheduler.requestUpdate(immediately = isNewPhase)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // The import's own end stops the service, after the staging the cancel deletes is gone: stopping here would
            // let the process be frozen halfway through deleting it.
            ACTION_CANCEL -> work.cancel()

            ACTION_DISMISS -> stop()

            else -> {
                words = intent?.getStringArrayListExtra(EXTRA_WORDS)?.let(ImportNotification::fromWords) ?: return START_NOT_STICKY
                if (work.state.value != ImportWorkState.IDLE) shownState = work.state.value
                val content = currentContent() ?: return START_NOT_STICKY
                val notification = buildNotification(content)
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
                )
                postedContent = content
                isInForeground = true
                isRunning = true
                // The work may have ended between the intent being sent and its arrival here.
                if (work.state.value == ImportWorkState.IDLE) scheduleStop()
            }
        }
        // Not sticky: an import whose process was killed is over, and a service restarted without it would show nothing real.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Android allows data sync services six hours of background time in a day, shared with sync's, and takes the app down
     * with a service still in the foreground a few seconds after saying they are up. The import is interrupted rather
     * than left to a process that may be frozen at any moment: its staging is deleted, and the import screen says why
     * with Try again when the user comes back. A write that has started is never interrupted, and finishes or stops with
     * the process, as it would without the service.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        work.interrupt()
        stop()
    }

    /**
     * Stops the service once no work has been going for the scheduler's grace, so that a download followed by its
     * unpacking keeps one notification. Only once in the foreground: stopping before then would leave the system waiting
     * for the `startForeground` that `startForegroundService` promised it, which it treats as a crash of the app.
     */
    private fun scheduleStop() {
        if (isInForeground) scheduler.scheduleRelease()
    }

    /**
     * Re-posted under the same id, which is how a foreground service's notification is updated; only once it is one, and
     * only where it would show something else.
     */
    private fun updateNotification() {
        if (!isInForeground) return
        val content = currentContent() ?: return
        if (content == postedContent) return
        postedContent = content
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun stop() {
        isInForeground = false
        postedContent = null
        isRunning = false
        scheduler.cancelRelease()
        scheduler.cancelUpdate()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** What the notification is to show now, null before the words have arrived. */
    private fun currentContent(): ImportNotificationContent? {
        val words = words ?: return null
        val state = work.state.value.takeIf { it != ImportWorkState.IDLE } ?: shownState
        return words.contentOf(state, work.currentProgress.value)
    }

    private fun buildNotification(content: ImportNotificationContent): Notification {
        val words = checkNotNull(words)
        createChannel(words.channelName)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_import)
            .setContentTitle(words.title)
            .setContentText(content.text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, CampfireMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .apply {
                // The write is never cancelled, so its notification offers nothing that would pretend to.
                if (content.isCancellable) {
                    addAction(
                        NotificationCompat.Action.Builder(
                            0,
                            words.cancelLabel,
                            PendingIntent.getService(
                                this@CampfireImportService,
                                1,
                                Intent(this@CampfireImportService, CampfireImportService::class.java).setAction(ACTION_CANCEL),
                                PendingIntent.FLAG_IMMUTABLE,
                            ),
                        ).build()
                    )
                }
            }
            .apply {
                val progress = content.progress
                if (progress != null) setProgress(IMPORT_NOTIFICATION_PROGRESS_STEPS, progress, false) else setProgress(0, 0, true)
            }
            .build()
    }

    /** Low importance, like sync's: something to be able to look at, not something to be interrupted by. */
    private fun createChannel(channelName: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    companion object {
        /** Whether a started service is in the foreground, for the activity to tell whether it needs starting. */
        @Volatile
        var isRunning = false
            private set

        private const val CHANNEL_ID = "import"

        /** Beside sync's 1 and the metronome's 2, so that an import and a sync at once show a notification each. */
        private const val NOTIFICATION_ID = 3
        private const val ACTION_CANCEL = "com.pandulapeter.campfire.action.CANCEL_IMPORT"
        private const val ACTION_DISMISS = "com.pandulapeter.campfire.action.DISMISS_IMPORT"
        private const val EXTRA_WORDS = "words"

        fun intent(context: Context, notification: ImportNotification) = Intent(context, CampfireImportService::class.java)
            .putStringArrayListExtra(EXTRA_WORDS, ArrayList(notification.toWords()))

        /** Takes the notification down because the app is back in front. Never cancels anything. */
        fun dismissIntent(context: Context) = Intent(context, CampfireImportService::class.java).setAction(ACTION_DISMISS)

        /**
         * Takes down a notification left behind by an import whose process never came back, from the next launch, as
         * `CampfireSyncService.clearStaleNotification` does for a sync run.
         */
        fun clearStaleNotification(context: Context) =
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }
}
