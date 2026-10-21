/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.sync

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.Navigator
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import com.pandulapeter.campfire.presentation.ui.platform.keepSyncConsentOrigin
import com.pandulapeter.campfire.presentation.ui.platform.takeSyncConsentOrigin
import com.pandulapeter.campfire.presentation.ui.screens.export.shareTargetOf
import com.pandulapeter.campfire.presentation.ui.screens.onboarding.OnboardingStep
import com.pandulapeter.campfire.presentation.ui.share.ShareTarget
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import kotlinx.serialization.Serializable

/**
 * The screen a Connect was tapped on, which is where the answer to its consent page is shown: the onboarding's Dropbox
 * page, the sync section of Settings, or the export screen's Link format for the song or setlist it was sharing.
 */
@Serializable
internal sealed interface SyncConsentOrigin {

    @Serializable
    data object Settings : SyncConsentOrigin

    @Serializable
    data object Onboarding : SyncConsentOrigin

    @Serializable
    data object OnboardingFromSettings : SyncConsentOrigin

    @Serializable
    data class Export(val target: ShareTarget) : SyncConsentOrigin
}

/**
 * Where a Connect tapped with [topDestination] on top of the back stack and [visibleDialog] over it was tapped: the
 * export screen where that is up, else Settings unless it is the onboarding.
 */
internal fun syncConsentOriginOf(topDestination: CampfireDestination?, visibleDialog: DialogType?): SyncConsentOrigin {
    val exportTarget = (visibleDialog as? DialogType.Export)?.let(::shareTargetOf)
    return when {
        exportTarget != null -> SyncConsentOrigin.Export(exportTarget)
        topDestination !is CampfireDestination.Onboarding -> SyncConsentOrigin.Settings
        topDestination.isOpenedFromSettings -> SyncConsentOrigin.OnboardingFromSettings
        else -> SyncConsentOrigin.Onboarding
    }
}

/** What showing a consent's answer does to the app, see [showSyncConsentAnswer]. */
internal enum class SyncConsentAnswer {
    NOTHING,
    ONBOARDING,
    ONBOARDING_FROM_SETTINGS,
    SETTINGS,
    ONBOARDING_ON_SYNC_PAGE,
    EXPORT,
}

/**
 * Where the answer to a consent asked from [origin] goes on [backStack]: only onto a stack nobody has built on since the
 * app started — the home screen, the settings screen Android brings back, or an owed onboarding still on its first page
 * ([onboardingStep]), which a start up that still owes it opens while the code is being exchanged, and
 * which is moved on to its Dropbox page rather than covered by Settings. The export screen, which is no destination, is
 * put back over whatever the start up's stack is: the home screen on the web, the screen under it that Android restores.
 */
internal fun syncConsentAnswerOf(backStack: List<CampfireDestination>, onboardingStep: OnboardingStep, origin: SyncConsentOrigin?) = when {
    backStack.drop(1) == listOf(CampfireDestination.Onboarding(isOpenedFromSettings = false)) ->
        if (onboardingStep == OnboardingStep.entries.first()) SyncConsentAnswer.ONBOARDING_ON_SYNC_PAGE else SyncConsentAnswer.NOTHING
    origin is SyncConsentOrigin.Export -> SyncConsentAnswer.EXPORT
    backStack.size > 1 && backStack.drop(1) != listOf(CampfireDestination.Settings) -> SyncConsentAnswer.NOTHING
    origin == SyncConsentOrigin.Onboarding -> SyncConsentAnswer.ONBOARDING
    origin == SyncConsentOrigin.OnboardingFromSettings -> SyncConsentAnswer.ONBOARDING_FROM_SETTINGS
    else -> SyncConsentAnswer.SETTINGS
}

/**
 * Keeps where a Connect was tapped for the start up its consent page may be answered in: the web's session storage
 * ([keepSyncConsentOrigin]), and the saved state Android restores a process reclaimed behind the browser from, which
 * brings back the back stack but not the export screen, a dialog to the view model.
 */
internal class SyncConsentOrigins(
    private val savedStateStore: SavedStateStore,
) {

    fun keep(origin: SyncConsentOrigin) {
        keepSyncConsentOrigin(origin)
        savedStateStore.persist(SYNC_CONSENT_ORIGIN_KEY, origin)
    }

    /** What [keep] kept, forgotten as it is read, so that it cannot be mistaken later for where another consent was asked from. */
    fun take(): SyncConsentOrigin? = (takeSyncConsentOrigin() ?: savedStateStore.restore<SyncConsentOrigin>(SYNC_CONSENT_ORIGIN_KEY)).also {
        savedStateStore.persist<SyncConsentOrigin?>(SYNC_CONSENT_ORIGIN_KEY, null)
    }

    private companion object {
        const val SYNC_CONSENT_ORIGIN_KEY = "syncConsentOrigin"
    }
}

/**
 * Shows the answer to a consent page the app was sent away to, which is where the user was when they left
 * ([SyncConsentOrigins.take]): the onboarding's Dropbox page, over the settings screen's About tab where that was what
 * opened it, Settings, on the tab holding the sync section, or the export screen on the Link format ([openExport]). Only onto a stack nobody has built on since the app started - the
 * home screen the app opens on, the settings screen Android brings back after reclaiming the process behind the browser, or
 * an owed onboarding still on its first page, which a start up that still owes it opens while the
 * code is being exchanged and which is moved on to its Dropbox page (see [syncConsentAnswerOf]); an onboarding
 * Android brings back is already on the page that says how it went. The code exchange this
 * follows can take a minute on a bad network, and a user who has gone somewhere else meanwhile has moved on: the
 * settings screen says the same thing whenever they get there, and an editor they opened in the meantime holds text
 * that nothing but its own ways out may take away.
 */
internal fun Navigator.showSyncConsentAnswer(origin: SyncConsentOrigin?, openExport: (ShareTarget) -> Unit) {
    when (syncConsentAnswerOf(backStack = backStack.toList(), onboardingStep = onboardingStep, origin = origin)) {
        SyncConsentAnswer.NOTHING -> Unit
        SyncConsentAnswer.ONBOARDING -> openOnboarding(isOpenedFromSettings = false, step = OnboardingStep.SYNC)
        SyncConsentAnswer.ONBOARDING_FROM_SETTINGS -> {
            openSettingsOn(SettingsTab.ABOUT)
            openOnboarding(isOpenedFromSettings = true, step = OnboardingStep.SYNC)
        }
        SyncConsentAnswer.SETTINGS -> openSettingsOn(SettingsTab.LIBRARY)
        SyncConsentAnswer.ONBOARDING_ON_SYNC_PAGE -> showOnboardingStepIfOpen(OnboardingStep.SYNC)
        SyncConsentAnswer.EXPORT -> (origin as? SyncConsentOrigin.Export)?.let { openExport(it.target) }
    }
}

private fun Navigator.openSettingsOn(tab: SettingsTab) {
    // Not through the re-selection of an open settings screen, which would send it back to General a moment after.
    if (backStack.lastOrNull() != CampfireDestination.Settings) selectTopLevelDestination(CampfireDestination.Settings)
    settingsTab = tab
}
