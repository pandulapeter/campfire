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

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Play's in-app updates, driven from the composition rather than from the activity.
 *
 * Everything this needs from Android - the activity to start the flow over, the launcher its result comes back to,
 * the lifecycle the checks are timed by - a composable can ask for itself, which is why this lives here next to the
 * shared UI instead of in `:app:android`. It is the same arrangement as the file picker and the notification
 * permission: the shell only provides what the shared UI genuinely cannot reach.
 *
 * A build the Play Store did not install answers every check with an error, so nothing of this shows up in a debug
 * APK, in a sideloaded release, or on a device without Play. The flow can only be exercised from a Play track.
 */
@Composable
internal actual fun rememberAppUpdateController(): AppUpdateController {
    val activity = LocalActivity.current as? ComponentActivity ?: return NoAppUpdates
    // Kept outside the controller so that a rotation does not undo a "later": the activity, and with it everything
    // remembered against it, is recreated, while the answer the user already gave should still stand.
    val isPostponed = rememberSaveable { mutableStateOf(false) }
    val controller = remember(activity) { AndroidAppUpdateController(activity, isPostponed) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        controller.onUpdateFlowResult(isSuccessful = result.resultCode == Activity.RESULT_OK)
    }
    SideEffect { controller.attachLauncher(launcher) }
    // Play answers with what it knows at the moment it is asked, and what it knows mostly changes while the app is
    // not the thing on screen: an immediate update the user walked out of is still in progress, and a flexible
    // download that finished in the background has nothing else to announce itself with.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.checkForUpdate() }
    DisposableEffect(controller) { onDispose(controller::release) }
    return controller
}

@Stable
private class AndroidAppUpdateController(
    activity: ComponentActivity,
    isPostponed: MutableState<Boolean>,
) : AppUpdateController {

    private val appUpdateManager = AppUpdateManagerFactory.create(activity.applicationContext)
    private val installStateListener = InstallStateUpdatedListener(::onInstallStateChanged)
    private var isPostponed by isPostponed
    private var availableUpdate: AppUpdateInfo? = null
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var isListenerRegistered = false
    private var hasStartedImmediateFlow = false

    override var state by mutableStateOf(AppUpdateState.NotAvailable)
        private set

    /** Only a composition can register one, so the launcher arrives after the controller rather than with it. */
    fun attachLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        this.launcher = launcher
    }

    fun checkForUpdate() {
        // A running download reports itself through the install listener. Asking Play again during one would answer
        // with the state the download started from and take the progress back off the screen.
        if (state == AppUpdateState.Downloading) return
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener(::onAppUpdateInfoReceived)
            // A failed check is the normal answer for a build Play did not install, and there is nothing the user
            // could do about a real failure either: the app simply stays the version it is.
            .addOnFailureListener { }
    }

    fun onUpdateFlowResult(isSuccessful: Boolean) {
        // Backing out of the Play sheet is the user declining the download, and only the flexible flow can be
        // declined without the app noticing otherwise - an immediate one is re-offered by the next check.
        if (!isSuccessful && state == AppUpdateState.Downloading) {
            unregisterInstallListener()
            postponeUpdate()
        }
    }

    fun release() = unregisterInstallListener()

    override fun startUpdate() {
        val appUpdateInfo = availableUpdate ?: return
        val launcher = launcher ?: return
        isPostponed = false
        when (state) {
            AppUpdateState.Required -> {
                hasStartedImmediateFlow = true
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    launcher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                )
            }

            AppUpdateState.Optional -> {
                registerInstallListener()
                // Said before Play is asked, so that the hint is gone by the time its sheet is over the app.
                state = AppUpdateState.Downloading
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    launcher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                )
            }

            else -> Unit
        }
    }

    override fun postponeUpdate() {
        isPostponed = true
        state = AppUpdateState.NotAvailable
    }

    override fun installUpdate() {
        appUpdateManager.completeUpdate()
    }

    private fun onAppUpdateInfoReceived(appUpdateInfo: AppUpdateInfo) {
        availableUpdate = appUpdateInfo
        state = appUpdateInfo.toAppUpdateState()
        // A required update is started without asking, but only the first time: after that the blocking screen's
        // own button is what starts it again, so that cancelling the Play flow cannot turn into a loop of the app
        // reopening it the moment the user is back.
        if (state == AppUpdateState.Required && !hasStartedImmediateFlow) startUpdate()
    }

    private fun onInstallStateChanged(installState: InstallState) {
        when (installState.installStatus()) {
            InstallStatus.DOWNLOADING -> state = AppUpdateState.Downloading

            InstallStatus.DOWNLOADED -> {
                unregisterInstallListener()
                state = AppUpdateState.ReadyToInstall
            }

            InstallStatus.CANCELED, InstallStatus.FAILED -> {
                unregisterInstallListener()
                state = AppUpdateState.NotAvailable
            }

            else -> Unit
        }
    }

    /**
     * Play's 0-5 `updatePriority`, which is set per release in the Play Console and is the only place this policy
     * is decided: 0-1 is left to Play's own schedule, 2-3 is worth a dismissible hint, and 4-5 is a release nobody
     * should be left on - a build that damages the library or cannot sync any more.
     *
     * `isUpdateTypeAllowed` is asked rather than assumed, because Play refuses a flow it cannot run (a device with
     * no room for the download, an update the account is not eligible for), and an offer that goes nowhere is worse
     * than no offer at all.
     */
    private fun AppUpdateInfo.toAppUpdateState() = when {
        // An immediate update the user walked out of halfway; Play resumes it, and until it does the app is the old
        // one, which is exactly what the blocking screen is for.
        updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> AppUpdateState.Required

        installStatus() == InstallStatus.DOWNLOADED -> if (isPostponed) AppUpdateState.NotAvailable else AppUpdateState.ReadyToInstall

        updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE -> AppUpdateState.NotAvailable

        updatePriority() >= MINIMUM_REQUIRED_PRIORITY ->
            if (isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) AppUpdateState.Required else AppUpdateState.NotAvailable

        updatePriority() >= MINIMUM_OPTIONAL_PRIORITY ->
            if (!isPostponed && isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) AppUpdateState.Optional else AppUpdateState.NotAvailable

        else -> AppUpdateState.NotAvailable
    }

    private fun registerInstallListener() {
        if (isListenerRegistered) return
        isListenerRegistered = true
        appUpdateManager.registerListener(installStateListener)
    }

    private fun unregisterInstallListener() {
        if (!isListenerRegistered) return
        isListenerRegistered = false
        appUpdateManager.unregisterListener(installStateListener)
    }
}

private const val MINIMUM_OPTIONAL_PRIORITY = 2
private const val MINIMUM_REQUIRED_PRIORITY = 4
