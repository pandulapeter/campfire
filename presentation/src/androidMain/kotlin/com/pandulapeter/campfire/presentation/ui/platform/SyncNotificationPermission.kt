/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Asks for the notification permission once sync is connected, which is the only thing Campfire ever posts a
 * notification for.
 *
 * On Android 13 and above a foreground service still starts without it - the run is kept alive either way - but its
 * notification is silently dropped, so the user is left with a sync they cannot see, cannot follow and cannot stop
 * from outside the app. Nothing else here depends on it: a refusal costs the notification, not the sync, which is
 * why this asks and then never mentions it again.
 *
 * Asked at the moment sync is switched on rather than at startup, because that is the first point at which the
 * question means anything to the user. That moment includes an app opened with sync already connected, so that the
 * people who connected it before this existed are asked too; [hasAsked] keeps it to one prompt per launch, and
 * `rememberSaveable` keeps a rotation from counting as a second one. Once the permission is granted the check below
 * ends it for good, and once Android has taken two refusals it stops showing the dialog by itself and the launcher
 * returns without any UI.
 */
@Composable
internal fun SyncNotificationPermissionEffect(isSyncConnected: Boolean) {
    val context = LocalContext.current
    // The result is deliberately ignored: there is nothing to do about a refusal, and nothing to say about a grant.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var hasAsked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isSyncConnected, hasAsked) {
        if (!isSyncConnected || hasAsked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        hasAsked = true
        // Context.checkSelfPermission rather than the androidx one, so that this file needs no dependency of its
        // own: it has been on Context since well before minSdk.
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
