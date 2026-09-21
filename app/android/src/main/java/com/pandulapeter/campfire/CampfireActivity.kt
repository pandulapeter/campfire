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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.pandulapeter.campfire.data.source.remote.implementation.auth.isSyncRedirect
import com.pandulapeter.campfire.data.source.remote.implementation.auth.onSyncRedirectReceived
import com.pandulapeter.campfire.presentation.ui.CampfireAndroidApp
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotifier
import com.pandulapeter.campfire.sync.CampfireSyncService

class CampfireActivity : AppCompatActivity() {

    private var isAppReady = false

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
                onAppReady = { isAppReady = true },
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
     * Starts and stops the service that keeps a sync run alive once the user has left the app. Building the
     * notification is the service's job; what arrives here is only whether there is one and what it should say.
     */
    private fun onSyncNotificationChanged(notification: com.pandulapeter.campfire.presentation.ui.platform.SyncNotification?) {
        try {
            if (notification == null) {
                startService(CampfireSyncService.dismissIntent(this))
            } else {
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
            }
        } catch (exception: Exception) {
            // A notification that cannot be shown must never take the sync down with it: the run carries on, it
            // just stops surviving the app being left.
            println("Could not update the sync service: ${exception.message}")
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

    private fun openUrl(url: String, isDarkTheme: Boolean) = try {
        CustomTabsIntent.Builder()
            .setColorScheme(if (isDarkTheme) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
            .build()
            .launchUrl(this, url.toUri())
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableExtra(name: String): Uri? = getParcelableExtra(name)

    @Suppress("DEPRECATION")
    private fun Intent.parcelableArrayListExtra(name: String): List<Uri>? = getParcelableArrayListExtra(name)
}
