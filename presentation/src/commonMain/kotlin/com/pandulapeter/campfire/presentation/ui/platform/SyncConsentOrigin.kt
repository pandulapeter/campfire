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

import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/**
 * The screen a Connect was tapped on, which is where the answer to its consent page is shown: the onboarding's Dropbox
 * page, or the sync section of Settings.
 */
internal enum class SyncConsentOrigin {
    SETTINGS,
    ONBOARDING,
    ONBOARDING_FROM_SETTINGS,
}

/** Where a Connect tapped with [topDestination] on top of the back stack was tapped: Settings unless it is the onboarding. */
internal fun syncConsentOriginOf(topDestination: CampfireDestination?) = when {
    topDestination !is CampfireDestination.Onboarding -> SyncConsentOrigin.SETTINGS
    topDestination.isOpenedFromSettings -> SyncConsentOrigin.ONBOARDING_FROM_SETTINGS
    else -> SyncConsentOrigin.ONBOARDING
}

/**
 * Remembers [origin] for [takeSyncConsentOrigin], across the start up the consent page may be answered in. Only the web
 * keeps it, since only there does every consent end in a new start: the app lives on everywhere else, and an Android
 * process reclaimed behind the browser comes back on its saved back stack, which already says where the tap was made.
 */
internal expect fun keepSyncConsentOrigin(origin: SyncConsentOrigin)

/** What [keepSyncConsentOrigin] remembered, forgotten as it is read; null where nothing was, or nothing could be. */
internal expect fun takeSyncConsentOrigin(): SyncConsentOrigin?
