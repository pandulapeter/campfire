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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * What the store the app came from has to say about a newer build of it.
 *
 * Only the Android build ever leaves [NotAvailable]: Play is the one store of the four with an API for this. The App
 * Store has no equivalent - the only way to ask it would be to poll Apple's public lookup endpoint for the published
 * version number, which would make it the second thing in the app that talks to the network, and iOS updates apps by
 * itself anyway. The desktop installers and the web build answer to no store at all, and the web one is a page that
 * is downloaded again every time it is opened.
 */
internal enum class AppUpdateState {

    /** Nothing to offer, which includes an update the store is happy to install on its own schedule. */
    NotAvailable,

    /** A newer build is worth mentioning, and the user may say no to it. */
    Optional,

    /** A newer build the app insists on; see [AppUpdateGate], which stops the app being usable until it is there. */
    Required,

    /** An accepted optional update is being fetched in the background, which the store reports on its own. */
    Downloading,

    /** The update is on the device and needs the app to restart before it is the one running. */
    ReadyToInstall,
}

/**
 * The little a screen needs in order to act on an [AppUpdateState]: what it is, and the three answers to it.
 *
 * There is no way to ask for a check. The platform that has updates at all knows better than the UI when to look -
 * an update accepted but never finished, and a download that completed while the app was away, are both things only
 * the store can announce - so the implementation drives its own checks from the lifecycle.
 */
@Stable
internal interface AppUpdateController {

    val state: AppUpdateState

    /** Hands the user over to the store, in the way [state] calls for. */
    fun startUpdate()

    /** Answers "later": nothing more is said about this update for as long as the app stays open. */
    fun postponeUpdate()

    /** Restarts the app into the update that has already been downloaded. */
    fun installUpdate()

    /**
     * Leaves the app, which is what back means on the screen a [AppUpdateState.Required] update puts in the way:
     * the app is still composed behind it, so a back gesture allowed through would navigate one the user cannot see.
     */
    fun closeApp()
}

/** The whole of the answer on the three platforms with no store to ask. */
internal object NoAppUpdates : AppUpdateController {

    override val state = AppUpdateState.NotAvailable

    override fun startUpdate() = Unit

    override fun postponeUpdate() = Unit

    override fun installUpdate() = Unit

    override fun closeApp() = Unit
}

/**
 * Provides the controller [AppUpdateGate] renders, tied to the composition it is called from: the Android one
 * registers an activity result launcher and watches the lifecycle, neither of which outlives the screen.
 */
@Composable
internal expect fun rememberAppUpdateController(): AppUpdateController
