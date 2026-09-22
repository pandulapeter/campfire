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
    // Kept outside the controller so that a rotation does not undo them: the activity, and with it everything
    // remembered against it, is recreated, while a "later" the user already gave should still stand, an
    // immediate flow they already backed out of should not open itself again, and what Play last said should be
    // on screen from the first frame rather than a few hundred milliseconds later - long enough for a rotation to
    // uncover an app the blocking screen was keeping the user out of.
    val isPostponed = rememberSaveable { mutableStateOf(false) }
    val hasStartedImmediateFlow = rememberSaveable { mutableStateOf(false) }
    val lastKnownState = rememberSaveable { mutableStateOf(AppUpdateState.NotAvailable) }
    val controller = remember(activity) {
        AndroidAppUpdateController(
            activity = activity,
            isPostponed = isPostponed,
            hasStartedImmediateFlow = hasStartedImmediateFlow,
            lastKnownState = lastKnownState,
        )
    }
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
    private val activity: ComponentActivity,
    isPostponed: MutableState<Boolean>,
    hasStartedImmediateFlow: MutableState<Boolean>,
    lastKnownState: MutableState<AppUpdateState>,
) : AppUpdateController {

    private val appUpdateManager = AppUpdateManagerFactory.create(activity.applicationContext)
    private val installStateListener = InstallStateUpdatedListener(::onInstallStateChanged)
    private var isPostponed by isPostponed
    private var availableUpdate: AppUpdateInfo? = null
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var isListenerRegistered = false
    private var hasStartedImmediateFlow by hasStartedImmediateFlow

    /**
     * Play's answer to a check is not tied to the activity that asked: it can arrive after this controller has left
     * the composition, when the launcher it holds is no longer registered and launching it throws.
     */
    private var isReleased = false

    /**
     * Starts from what the controller of the previous activity knew, which Play corrects with the first answer.
     * A download is the exception: it draws nothing, and the first answer is what registers a listener for its end.
     */
    override var state by lastKnownState
        private set

    init {
        if (state == AppUpdateState.Downloading) state = AppUpdateState.NotAvailable
    }

    /** Only a composition can register one, so the launcher arrives after the controller rather than with it. */
    fun attachLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        this.launcher = launcher
    }

    fun checkForUpdate() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener(::onAppUpdateInfoReceived)
            // A failed check is the normal answer for a build Play did not install, and there is nothing the user
            // could do about a real failure either: the app simply stays the version it is. A state carried over
            // from the previous activity is only believed until Play has been asked, though, and one it has not
            // confirmed is not left standing - above all not a blocking screen whose button needs Play's answer.
            .addOnFailureListener { if (!isReleased && availableUpdate == null) state = AppUpdateState.NotAvailable }
    }

    fun onUpdateFlowResult(isSuccessful: Boolean) {
        // Backing out of the Play sheet is the user declining the download, and only the flexible flow can be
        // declined - an immediate one is re-offered by the next check, which does not ask whether it was postponed.
        // The state is not asked whether it is a download: a sheet that was open while the activity was recreated
        // answers to a controller that has not heard from Play yet.
        if (!isSuccessful && state != AppUpdateState.Required) {
            unregisterInstallListener()
            postponeUpdate()
        }
    }

    fun release() {
        isReleased = true
        launcher = null
        unregisterInstallListener()
    }

    override fun startUpdate() {
        val appUpdateInfo = availableUpdate ?: return
        val launcher = launcher ?: return
        isPostponed = false
        when (state) {
            AppUpdateState.Required -> {
                hasStartedImmediateFlow = true
                // The blocking screen stays whatever happens here, and its button is the way to try again.
                if (!launchUpdateFlow(appUpdateInfo, launcher, AppUpdateType.IMMEDIATE)) checkForUpdate()
            }

            AppUpdateState.Optional -> {
                registerInstallListener()
                // Said before Play is asked, so that the hint is gone by the time its sheet is over the app.
                state = AppUpdateState.Downloading
                if (!launchUpdateFlow(appUpdateInfo, launcher, AppUpdateType.FLEXIBLE)) {
                    unregisterInstallListener()
                    state = AppUpdateState.NotAvailable
                    checkForUpdate()
                }
            }

            else -> Unit
        }
    }

    /**
     * Whether Play took the flow. An [AppUpdateInfo] starts one flow and no more, and the launcher belongs to a
     * composition that may be gone, so this can throw as well as refuse; either way the answer in hand is spent,
     * and the caller asks for a new one.
     */
    private fun launchUpdateFlow(
        appUpdateInfo: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        @AppUpdateType updateType: Int,
    ) = try {
        appUpdateManager.startUpdateFlowForResult(appUpdateInfo, launcher, AppUpdateOptions.newBuilder(updateType).build())
    } catch (exception: Exception) {
        false
    }

    override fun startRequiredUpdateOnce() {
        if (state == AppUpdateState.Required && !hasStartedImmediateFlow) startUpdate()
    }

    override fun postponeUpdate() {
        isPostponed = true
        state = AppUpdateState.NotAvailable
    }

    override fun installUpdate() {
        appUpdateManager.completeUpdate()
    }

    override fun closeApp() {
        activity.finish()
    }

    private fun onAppUpdateInfoReceived(appUpdateInfo: AppUpdateInfo) {
        if (isReleased) return
        availableUpdate = appUpdateInfo
        val newState = appUpdateInfo.toAppUpdateState()
        // Play takes a moment to notice a download it has just been asked for, and the check that runs as its sheet
        // closes can still be answered with the offer. The offer has been taken, so it is not made again.
        if (state == AppUpdateState.Downloading && newState == AppUpdateState.Optional) return
        state = newState
        // A download this controller did not start - one that was going when the activity was recreated - has
        // nobody listening for its end yet.
        if (newState == AppUpdateState.Downloading) registerInstallListener()
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
    private fun AppUpdateInfo.toAppUpdateState(): AppUpdateState {
        val isRequired = updatePriority() >= MINIMUM_REQUIRED_PRIORITY
        val isInProgress = updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
        return when {
            // An immediate update the user walked out of halfway; Play resumes it, and until it does the app is the
            // old one, which is exactly what the blocking screen is for. Play does not say which kind of flow is in
            // progress, but the priority does: a required update is never started as anything else.
            isInProgress && isRequired -> AppUpdateState.Required

            installStatus() == InstallStatus.DOWNLOADED -> if (isPostponed) AppUpdateState.NotAvailable else AppUpdateState.ReadyToInstall

            // A flexible download that is going already, which is what a recreated activity finds.
            installStatus() == InstallStatus.PENDING || installStatus() == InstallStatus.DOWNLOADING -> AppUpdateState.Downloading

            updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE -> AppUpdateState.NotAvailable

            isRequired -> if (isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) AppUpdateState.Required else AppUpdateState.NotAvailable

            updatePriority() >= MINIMUM_OPTIONAL_PRIORITY ->
                if (!isPostponed && isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) AppUpdateState.Optional else AppUpdateState.NotAvailable

            else -> AppUpdateState.NotAvailable
        }
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
