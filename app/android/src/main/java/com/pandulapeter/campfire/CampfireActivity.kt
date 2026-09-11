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
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.source.remote.implementation.auth.isSyncRedirect
import com.pandulapeter.campfire.data.source.remote.implementation.auth.onSyncRedirectReceived
import com.pandulapeter.campfire.presentation.ui.CampfireAndroidApp
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotifier
import com.pandulapeter.campfire.sync.CampfireSyncService
import com.pandulapeter.campfire.presentation.ui.platform.toImportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CampfireActivity : AppCompatActivity() {

    // The activity is singleTask, so a second file opened while Campfire is running arrives at onNewIntent rather
    // than at a new instance; both ends up here.
    private val filesToImport = Channel<List<ImportedFile>>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampfireAndroidApp(
                urlOpener = ::openUrl,
                filesToImport = filesToImport.receiveAsFlow(),
                syncNotifier = ::onSyncNotificationChanged,
            )
        }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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

    /** The URIs an "open with" or a share carries, whichever of the three shapes the intent uses. */
    private fun importFrom(intent: Intent?) {
        val uris = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            // Reading them is disk work, and the intent arrives on the main thread.
            val files = withContext(Dispatchers.IO) { uris.mapNotNull { it.toImportedFile(this@CampfireActivity) } }
            if (files.isNotEmpty()) {
                filesToImport.send(files)
            }
        }
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
