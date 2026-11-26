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
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * `checkSelfPermission`, read again on every resume, the Activity's result launcher, and the app's page of the system
 * settings. A refusal with no rationale owed after it is one the system no longer shows a question for, which Open
 * settings then answers instead of Ask again; Android says the same of a question never asked, so whether this device
 * has asked ([hasAsked], kept across launches) is what tells the two apart. A grant taken back in the system's settings
 * reads as refused for good too, which Open settings still answers.
 */
@Composable
internal actual fun rememberMicrophonePermission(hasAsked: Boolean, onAnswered: () -> Unit): MicrophonePermission {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isGranted by remember { mutableStateOf(context.isMicrophoneGranted()) }
    var wasRefused by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isGranted = granted
        wasRefused = !granted
        onAnswered()
    }
    LifecycleResumeEffect(context) {
        isGranted = context.isMicrophoneGranted()
        if (isGranted) wasRefused = false
        onPauseOrDispose { }
    }
    val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true
    val isRefusedForGood = !isGranted && !rationale && (wasRefused || hasAsked)
    return MicrophonePermission(
        status = when {
            isGranted -> MicrophoneStatus.GRANTED
            rationale || isRefusedForGood -> MicrophoneStatus.DENIED
            else -> MicrophoneStatus.NOT_ASKED
        },
        canAskAgain = !isRefusedForGood,
        request = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
        openSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}

private fun Context.isMicrophoneGranted() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
