/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.remote.implementation.auth.isSyncRedirect
import com.pandulapeter.campfire.data.source.remote.implementation.auth.onSyncRedirectReceived
import com.pandulapeter.campfire.presentation.ui.CampfireAndroidApp
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotification
import com.pandulapeter.campfire.metronome.CampfireMetronomeService
import com.pandulapeter.campfire.presentation.ui.platform.MetronomeNotification
import com.pandulapeter.campfire.sync.CampfireSyncService

class CampfireMainActivity : ComponentActivity() {

    private var isAppReady = false

    /** The theme color the launcher icon is to be in, known once the preferences have been read. */
    private var appIconColor: UserPreferences.ThemeColor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        keepStartupScreenUntilAppIsReady()
        // An intent is acted on once. A recreated activity is handed the intent of the instance before it - on a
        // rotation, and when the system brings the app back after killing it - and one reopened from Recents the
        // intent it was first started with, marked as history. Neither is something the user has just asked for,
        // and importing the same file again would put the conflicts question up over whatever they did to the
        // song since. An intent that arrives while no instance exists is not lost to this: the system delivers it
        // to onNewIntent once there is one.
        // Ahead of the content on purpose: a redirect that started this process answers an authorization the previous
        // one was killed in the middle of, and it has to be waiting by the time the first composition creates the view
        // model, whose start up asks for it exactly once.
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            handle(intent)
        }
        setContent {
            CampfireAndroidApp(
                urlOpener = ::openUrl,
                filesToImport = filesToImport,
                syncNotifier = ::onSyncNotificationChanged,
                metronomeNotifier = ::onMetronomeNotificationChanged,
                onAppReady = { isAppReady = true },
                onAppIconChanged = { themeColor ->
                    appIconColor = themeColor
                    AppIconSwitcher.apply(context = this, themeColor = themeColor, isLeaving = false)
                },
            )
        }
    }

    /**
     * Holds whatever the system is showing while the app starts - the splash screen on Android 12 and above, the
     * window background below it - until there is an app behind it to uncover.
     *
     * The system takes it away as soon as the first frame is drawn, and that frame is not the app: the preferences
     * that decide the palette and the language have not been read yet, so it is Campfire's own launch screen. Left
     * alone, the splash would hand over to that and the user would watch two startup screens in a row. Refusing to
     * draw is what postpones the first frame, and with it the handover.
     */
    private fun keepStartupScreenUntilAppIsReady() {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!isAppReady) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    /**
     * Where the first switch of the launcher icon is made (see [AppIconSwitcher]), the one that closes the task: as the
     * user leaves. A stop that only recreates the activity is not leaving, and neither is one caused by another app's
     * screen coming up inside this task - the document picker, the share sheet, the consent page of sync - which the
     * switch would close along with it. Every later switch is made as the color is picked.
     */
    override fun onStop() {
        super.onStop()
        val appIconColor = appIconColor
        if (appIconColor != null && !isChangingConfigurations && !isCoveredWithinTask()) {
            AppIconSwitcher.apply(context = this, themeColor = appIconColor, isLeaving = true)
        }
    }

    private fun isCoveredWithinTask() = getSystemService(ActivityManager::class.java).appTasks.any { task ->
        task.taskInfo?.topActivity?.packageName.let { it != null && it != packageName }
    }

    /**
     * The activity is singleTask, so a file opened while Campfire is running arrives here rather than at a new
     * instance - as does one that arrives while the instance is gone, which the system holds until it is back.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /**
     * The two things the system can hand this activity: a file to open, and the answer to a sync authorization the
     * app sent the user to the browser for.
     */
    private fun handle(intent: Intent?) {
        val data = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && data != null && isSyncRedirect(data.toString())) {
            onSyncRedirectReceived(data.toString())
        } else {
            importFrom(intent)
        }
    }

    /**
     * The run the composition last said is going, or null when none is. Kept rather than acted on while the app is in
     * front, see [onSyncNotificationChanged].
     */
    private var syncNotification: SyncNotification? = null

    /**
     * The words the service was last started with. The counts need no forwarding - the service renders them from the
     * sync state itself - so a run's progress starts it again only when it is not running (it stops itself when it
     * sees a run end, which the activity may not have been watching) or when the words changed (a language switch).
     */
    private var lastSyncServiceWords: List<String>? = null

    /** Between a pause that was the user leaving - not a rotation - and the next resume. */
    private var isLeaving = false

    /**
     * Whether the service has been asked to start since the app was last left. [CampfireSyncService.isRunning] only
     * turns true once the service has got the intent, and the same run may be handed over again before that.
     */
    private var hasStartedSyncServiceSinceLeaving = false

    /**
     * The service is only started once the app is being left: in front, the activity keeps the process alive anyway,
     * and a foreground service shows its notification - Stop button and all - at once, which for the run every edit
     * starts ten seconds later would be a notification after nearly every change the user makes. What arrives while the
     * app is in front is kept for [onPause]; what arrives after it starts the service there and then.
     */
    private fun onSyncNotificationChanged(notification: SyncNotification?) {
        syncNotification = notification
        if (notification == null) {
            dismissSyncService()
        } else if (isLeaving) {
            startSyncService(notification)
        }
    }

    /**
     * Starts the metronome's service as a click starts, which is always a tap with the app in front, and hands it new
     * words as they change. Nothing is done for a click that ended: the service follows the engine itself, since this
     * activity may be gone by then.
     */
    private fun onMetronomeNotificationChanged(notification: MetronomeNotification?) {
        if (notification == null) return
        val intent = CampfireMetronomeService.intent(
            context = this,
            channelName = notification.channelName,
            title = notification.title,
            body = notification.body,
            stopLabel = notification.stopLabel,
        )
        try {
            if (CampfireMetronomeService.isRunning) startService(intent) else ContextCompat.startForegroundService(this, intent)
        } catch (exception: Exception) {
            // A notification that cannot be shown must never take the click down with it: it plays on, and only stops
            // surviving the app being left.
            println("Could not start the metronome service: ${exception.message}")
        }
    }

    /**
     * The last moment the service may be started - Android 12 refuses a foreground service started from the
     * background, and the activity is still visible here. A run that leaving the app has just started is already in
     * [syncNotification]: the composition hands it over from its own ON_PAUSE observer, which is told before this.
     * A pause on the way to a rotation is not leaving, and the activity that follows takes over.
     */
    override fun onPause() {
        super.onPause()
        if (isChangingConfigurations) return
        isLeaving = true
        syncNotification?.let(::startSyncService)
    }

    /**
     * Back in front, a run that is still going shows on the settings screen, and its notification goes; the service is
     * started again should the user leave before it ends.
     */
    override fun onResume() {
        super.onResume()
        isLeaving = false
        hasStartedSyncServiceSinceLeaving = false
        dismissSyncService()
    }

    /**
     * Starts the service that keeps a sync run alive once the user has left the app. Building the notification is the
     * service's job; what arrives here is only what it should say.
     */
    private fun startSyncService(notification: SyncNotification) {
        val words = listOf(
            notification.channelName,
            notification.title,
            notification.preparingBody,
            notification.progressBodyFormat,
            notification.stopLabel,
        )
        if (words == lastSyncServiceWords && (CampfireSyncService.isRunning || hasStartedSyncServiceSinceLeaving)) return
        try {
            ContextCompat.startForegroundService(
                this,
                CampfireSyncService.intent(
                    context = this,
                    channelName = notification.channelName,
                    title = notification.title,
                    preparingBody = notification.preparingBody,
                    progressBodyFormat = notification.progressBodyFormat,
                    stopLabel = notification.stopLabel,
                    completed = notification.progress.completed,
                    total = notification.progress.total,
                ),
            )
            lastSyncServiceWords = words
            hasStartedSyncServiceSinceLeaving = true
        } catch (exception: Exception) {
            // A notification that cannot be shown must never take the sync down with it: the run carries on, it
            // just stops surviving the app being left.
            println("Could not start the sync service: ${exception.message}")
        }
    }

    /**
     * Takes the service down, if this activity or an earlier one in the process started it. Never stops the run:
     * see [CampfireSyncService.dismissIntent].
     */
    private fun dismissSyncService() {
        if (lastSyncServiceWords == null && !CampfireSyncService.isRunning) return
        lastSyncServiceWords = null
        try {
            startService(CampfireSyncService.dismissIntent(this))
        } catch (exception: Exception) {
            println("Could not stop the sync service: ${exception.message}")
        }
    }

    /**
     * What an "open with" or a share carries, whichever of the three shapes the intent uses. A share is a file or
     * a piece of text, and the file wins where an intent has both: the text next to a stream is a caption for it
     * - the file's name, a "sent from" line - and not a second thing to import.
     */
    private fun importFrom(intent: Intent?) {
        val uris = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        when {
            uris.isNotEmpty() -> importFiles(uris)
            intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE ->
                importSharedTexts(texts = intent.sharedTexts(), subject = intent.getStringExtra(Intent.EXTRA_SUBJECT))
        }
    }

    /**
     * `EXTRA_TEXT` is one text for a `SEND` and a list of them for a `SEND_MULTIPLE`, asked for in that order
     * because the platform logs a warning for an extra asked for as the wrong type. Some senders fill in only the
     * clip data.
     */
    private fun Intent.sharedTexts(): List<String> {
        val single = { getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { listOf(it) } }
        val several = { getCharSequenceArrayListExtra(Intent.EXTRA_TEXT) }
        val texts = if (action == Intent.ACTION_SEND) single() ?: several() else several() ?: single()
        return (texts ?: clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).text } })
            .orEmpty()
            .map { it.toString() }
    }

    /**
     * Opens [url] in a Custom Tab, except a Play listing, which goes to the Play Store app: a Custom Tab shows it as a
     * web page that can only send the user on to the store. Play claims its own https addresses, so the same URL is
     * handed to it by package, and a device without Play gets the web page after all.
     */
    private fun openUrl(url: String, isDarkTheme: Boolean) {
        val uri = url.toUri()
        if (uri.host == PLAY_STORE_HOST) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(PLAY_STORE_PACKAGE))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        openInCustomTab(uri, isDarkTheme)
    }

    private fun openInCustomTab(uri: Uri, isDarkTheme: Boolean) = try {
        CustomTabsIntent.Builder()
            .setColorScheme(if (isDarkTheme) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
            .build()
            .launchUrl(this, uri)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableExtra(name: String): Uri? = getParcelableExtra(name)

    @Suppress("DEPRECATION")
    private fun Intent.parcelableArrayListExtra(name: String): List<Uri>? = getParcelableArrayListExtra(name)
}

private const val PLAY_STORE_HOST = "play.google.com"
private const val PLAY_STORE_PACKAGE = "com.android.vending"
