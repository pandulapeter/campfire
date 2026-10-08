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
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.remote.implementation.auth.isSyncRedirect
import com.pandulapeter.campfire.data.source.remote.implementation.auth.onSyncRedirectReceived
import com.pandulapeter.campfire.presentation.ui.CampfireAndroidApp
import com.pandulapeter.campfire.metronome.CampfireMetronomeService
import com.pandulapeter.campfire.sync.SyncServiceStarter

class CampfireMainActivity : ComponentActivity() {

    private var isAppReady = false

    private val syncServiceStarter = SyncServiceStarter(this)

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
                urlOpener = this::openUrl,
                filesToImport = filesToImport,
                syncNotifier = syncServiceStarter::onSyncNotificationChanged,
                metronomeNotifier = { notification ->
                    // Nothing is done for a click that ended: the service follows the engine itself, since this activity
                    // may be gone by then.
                    if (notification != null) CampfireMetronomeService.start(context = this, notification = notification)
                },
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
     * switch would close along with it, nor is a stop with a click playing, which is the screen being locked over a song
     * on a music stand: closing the task would silence it. Every later switch is made as the color is picked. Whether to
     * switch is decided here; the package manager's work runs on the switcher's own thread a moment after this returns.
     */
    override fun onStop() {
        super.onStop()
        val appIconColor = appIconColor
        if (appIconColor != null && !isChangingConfigurations && !isCoveredWithinTask() && !CampfireMetronomeService.isRunning) {
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
     * The last moment the service may be started - Android 12 refuses a foreground service started from the
     * background, and the activity is still visible here. A run that leaving the app has just started is already in
     * [syncServiceStarter]: the composition hands it over from its own ON_PAUSE observer, which is told before this.
     * A pause on the way to a rotation is not leaving, and the activity that follows takes over.
     */
    override fun onPause() {
        super.onPause()
        if (isChangingConfigurations) return
        syncServiceStarter.onLeaving()
    }

    /**
     * Back in front, a run that is still going shows on the settings screen, and its notification goes; the service is
     * started again should the user leave before it ends.
     */
    override fun onResume() {
        super.onResume()
        syncServiceStarter.onReturned()
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
}
