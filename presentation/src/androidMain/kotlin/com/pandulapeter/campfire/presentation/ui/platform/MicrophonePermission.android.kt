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
import android.content.SharedPreferences
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
 * settings then answers instead of Ask again; Android says the same of a question never asked and of a grant that
 * lapsed ("Only this time", the reset of an unused app's permissions, a restore to another phone), so a refusal for
 * good is told from the rationale owed before and after each request ([microphoneRefusalAfter]) and stored on this
 * device by itself. Its own preferences file stays out of the backups, which name only the library and the
 * preferences document, since the refusal belongs to this installation; any grant clears it. A grant taken back in
 * the system's settings reads as never asked, which the question then answers.
 */
@Composable
internal actual fun rememberMicrophonePermission(): MicrophonePermission {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val storedRefusal = remember(context) { StoredMicrophoneRefusal(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)) }
    var isGranted by remember { mutableStateOf(context.isMicrophoneGranted()) }
    // A state rather than read as it is composed: a first refusal changes neither the grant nor the stored refusal, so
    // only the rationale it leaves owed recomposes the notice into Ask again.
    var rationale by remember { mutableStateOf(activity?.shouldShowMicrophoneRationale() == true) }
    // Saved, since the Activity can be recreated under the system's question.
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isGranted = granted
        rationale = activity?.shouldShowMicrophoneRationale() == true
        val refusal = microphoneRefusalAfter(
            rationaleBefore = rationaleBefore,
            rationaleAfter = rationale,
            isGranted = granted,
            earlierSilentDenials = storedRefusal.value.silentDenials,
        )
        println("Microphone: ${if (granted) "granted" else if (refusal.isForGood) "refused for good" else "refused"}")
        storedRefusal.update(refusal)
    }
    // Saved, since the process can end while the system settings are in front.
    var isInSettings by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(context) {
        isGranted = context.isMicrophoneGranted()
        rationale = activity?.shouldShowMicrophoneRationale() == true
        if (isInSettings) {
            isInSettings = false
            storedRefusal.update(storedRefusal.value.afterSettings(isGranted))
        }
        if (isGranted) storedRefusal.update(MicrophoneRefusal())
        onPauseOrDispose { }
    }
    val state = androidMicrophoneStateOf(
        isGranted = isGranted,
        rationale = rationale,
        isRefusedForGood = storedRefusal.value.isForGood,
    )
    return MicrophonePermission(
        status = state.status,
        canAskAgain = state.canAskAgain,
        request = {
            rationaleBefore = activity?.shouldShowMicrophoneRationale() == true
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        },
        openSettings = {
            isInSettings = true
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}

/** The [MicrophoneRefusal] kept in the microphone's own preferences file, read once as the tuner is first shown. */
private class StoredMicrophoneRefusal(private val preferences: SharedPreferences) {
    var value by mutableStateOf(
        MicrophoneRefusal(
            isForGood = preferences.getBoolean(KEY_IS_FOR_GOOD, false),
            silentDenials = preferences.getInt(KEY_SILENT_DENIALS, 0),
        ),
    )
        private set

    fun update(refusal: MicrophoneRefusal) {
        if (refusal == value) return
        value = refusal
        preferences.edit()
            .putBoolean(KEY_IS_FOR_GOOD, refusal.isForGood)
            .putInt(KEY_SILENT_DENIALS, refusal.silentDenials)
            .apply()
    }
}

private const val PREFERENCES_NAME = "microphone_permission"
private const val KEY_IS_FOR_GOOD = "is_refused_for_good"
private const val KEY_SILENT_DENIALS = "silent_denials"

private fun Activity.shouldShowMicrophoneRationale() = shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)

private fun Context.isMicrophoneGranted() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
