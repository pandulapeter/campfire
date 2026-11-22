/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.update

/** Where Play's download of an update is, as far as [playUpdateStateOf] tells them apart. */
internal enum class PlayInstallStatus {
    PENDING,
    DOWNLOADING,
    DOWNLOADED,
    OTHER,
}

/**
 * What Play's answer about an update means for the app. [priority] is Play's 0-5 `updatePriority`, which is set per
 * release in the Play Console and is the only place this policy is decided: 0-1 is left to Play's own schedule, 2-3 is
 * worth a dismissible hint, and 4-5 is a release nobody should be left on - a build that damages the library or cannot
 * sync any more.
 *
 * Whether a flow is allowed ([isFlexibleAllowed], [isImmediateAllowed]) is asked rather than assumed, because Play
 * refuses a flow it cannot run (a device with no room for the download, an update the account is not eligible for), and
 * an offer that goes nowhere is worse than no offer at all.
 */
internal fun playUpdateStateOf(
    priority: Int,
    isUpdateAvailable: Boolean,
    isUpdateInProgress: Boolean,
    installStatus: PlayInstallStatus,
    isPostponed: Boolean,
    isFlexibleAllowed: Boolean,
    isImmediateAllowed: Boolean,
): AppUpdateState {
    val isRequired = priority >= MINIMUM_REQUIRED_PRIORITY
    return when {
        // An immediate update the user walked out of halfway; Play resumes it, and until it does the app is the old one,
        // which is exactly what the blocking screen is for. Play does not say which kind of flow is in progress, but the
        // priority does: a required update is never started as anything else.
        isUpdateInProgress && isRequired -> AppUpdateState.Required

        installStatus == PlayInstallStatus.DOWNLOADED -> if (isPostponed) AppUpdateState.NotAvailable else AppUpdateState.ReadyToInstall

        // A flexible download that is going already, which is what a recreated activity finds.
        installStatus == PlayInstallStatus.PENDING || installStatus == PlayInstallStatus.DOWNLOADING -> AppUpdateState.Downloading

        !isUpdateAvailable -> AppUpdateState.NotAvailable

        isRequired -> if (isImmediateAllowed) AppUpdateState.Required else AppUpdateState.NotAvailable

        priority >= MINIMUM_OPTIONAL_PRIORITY -> if (!isPostponed && isFlexibleAllowed) AppUpdateState.Optional else AppUpdateState.NotAvailable

        else -> AppUpdateState.NotAvailable
    }
}

private const val MINIMUM_OPTIONAL_PRIORITY = 2
private const val MINIMUM_REQUIRED_PRIORITY = 4
