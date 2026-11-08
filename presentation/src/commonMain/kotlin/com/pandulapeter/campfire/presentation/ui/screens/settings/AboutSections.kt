/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.CAMPFIRE_VERSION_NAME
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.resources.settings_onboarding
import com.pandulapeter.campfire.presentation.resources.settings_onboarding_description
import com.pandulapeter.campfire.presentation.resources.ic_bug
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.resources.ic_coffee
import com.pandulapeter.campfire.presentation.resources.ic_git_hub
import com.pandulapeter.campfire.presentation.resources.ic_keyboard
import com.pandulapeter.campfire.presentation.resources.keyboard_shortcuts
import com.pandulapeter.campfire.presentation.resources.ic_license
import com.pandulapeter.campfire.presentation.resources.ic_log
import com.pandulapeter.campfire.presentation.resources.ic_phone
import com.pandulapeter.campfire.presentation.resources.ic_privacy_policy
import com.pandulapeter.campfire.presentation.resources.ic_share
import com.pandulapeter.campfire.presentation.resources.ic_star
import com.pandulapeter.campfire.presentation.resources.ic_website
import com.pandulapeter.campfire.presentation.resources.settings_created_by
import com.pandulapeter.campfire.presentation.resources.settings_distribution_app_store
import com.pandulapeter.campfire.presentation.resources.settings_distribution_mac_app_store
import com.pandulapeter.campfire.presentation.resources.settings_distribution_microsoft_store
import com.pandulapeter.campfire.presentation.resources.settings_distribution_play_store
import com.pandulapeter.campfire.presentation.resources.settings_distributions_all
import com.pandulapeter.campfire.presentation.resources.settings_distributions_all_description
import com.pandulapeter.campfire.presentation.resources.settings_git_hub
import com.pandulapeter.campfire.presentation.resources.settings_git_hub_description
import com.pandulapeter.campfire.presentation.resources.settings_group_campfire
import com.pandulapeter.campfire.presentation.resources.settings_group_help
import com.pandulapeter.campfire.presentation.resources.settings_group_legal
import com.pandulapeter.campfire.presentation.resources.settings_help
import com.pandulapeter.campfire.presentation.resources.settings_help_description
import com.pandulapeter.campfire.presentation.resources.settings_keyboard_shortcuts_description
import com.pandulapeter.campfire.presentation.resources.settings_log_description
import com.pandulapeter.campfire.presentation.resources.settings_open_source_licenses
import com.pandulapeter.campfire.presentation.resources.settings_open_source_licenses_description
import com.pandulapeter.campfire.presentation.resources.settings_privacy_policy
import com.pandulapeter.campfire.presentation.resources.settings_privacy_policy_description
import com.pandulapeter.campfire.presentation.resources.settings_rate
import com.pandulapeter.campfire.presentation.resources.settings_rate_description
import com.pandulapeter.campfire.presentation.resources.settings_share_app
import com.pandulapeter.campfire.presentation.resources.settings_share_app_description
import com.pandulapeter.campfire.presentation.resources.settings_support
import com.pandulapeter.campfire.presentation.resources.settings_support_description
import com.pandulapeter.campfire.presentation.resources.settings_version
import com.pandulapeter.campfire.presentation.resources.settings_website
import com.pandulapeter.campfire.presentation.ui.screens.settings.LinkListItem
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.LocalShowsShortcutHints
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.ShortcutPlatforms
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.processLogActionTitle
import com.pandulapeter.campfire.presentation.ui.platform.Distribution
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.appBuild
import com.pandulapeter.campfire.presentation.ui.platform.canAskForDonations
import com.pandulapeter.campfire.presentation.ui.platform.platformStore
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection
import org.jetbrains.compose.resources.painterResource

/**
 * What the app is and where it lives, in three groups: the app itself (its website, where every build of it is, how to
 * give something back, and, last, who made it and which version this is); help (the keyboard shortcuts where there is a
 * keyboard, the website's support page, the source code and issue tracker, and the app's log for a report there); and
 * the fine print. The row that names the author is the link to the
 * author's own site, since that is what a name with a link on it is expected to lead to. campfire-songbook.com is the
 * app's home page: every build of Campfire is listed there, which is the whole of what the app says about the other
 * platforms, since a page can be kept up to date without a release and it is the one place App Review has nothing to
 * say about; and its support page answers the common questions and says how to reach the author, by email or on
 * GitHub. GitHub comes after those, as the source code and the issue tracker, since that is what it is to somebody who
 * has never used it.
 *
 * The rating row leads to the store of the platform the app is running on, and only once that listing exists. It
 * says "rate", never "get it from the store": a store page has an install button on it, and somebody who has the
 * direct download would end up with a second copy of the app, sandboxed, with a library of its own. For the same
 * reason the share row sends the website's address, never a store listing, and it is there only where the platform
 * has a share sheet: elsewhere it would be the download row again. Nothing is ever given for a share, which both
 * stores forbid.
 */
@Composable
internal fun AboutSections(
    showDialog: (DialogType) -> Unit,
    onOnboardingClicked: () -> Unit,
    onShareLink: (filePicker: FilePicker, link: String, subject: String) -> Unit,
    urlOpener: (String) -> Unit,
) {
    val filePicker = LocalFilePicker.current
    AppSection(
        urlOpener = urlOpener,
        onShareClicked = if (filePicker.canShare) {
            { onShareLink(filePicker, WEBSITE_URL, "Campfire") }
        } else {
            null
        },
    )
    HelpSection(
        urlOpener = urlOpener,
        processLogTitle = processLogActionTitle(filePicker),
        onProcessLogClicked = { showDialog(DialogType.SendProcessLog(isAfterCrash = false)) },
        onKeyboardShortcutsClicked = { showDialog(DialogType.KeyboardShortcuts) },
        onOnboardingClicked = onOnboardingClicked,
    )
    LegalSection(urlOpener = urlOpener, onOpenSourceLicensesClicked = { showDialog(DialogType.OpenSourceLicenses) })
}

@Composable
private fun AppSection(
    urlOpener: (String) -> Unit,
    onShareClicked: (() -> Unit)?,
) = SettingsSection(title = stringResource(Res.string.settings_group_campfire)) {
    LinkListItem(
        title = stringResource(Res.string.settings_website),
        description = WEBSITE_HOST,
        icon = painterResource(Res.drawable.ic_website),
        onClick = { urlOpener(WEBSITE_URL) },
    )
    LinkListItem(
        title = stringResource(Res.string.settings_distributions_all),
        description = stringResource(Res.string.settings_distributions_all_description),
        icon = painterResource(Res.drawable.ic_phone),
        onClick = { urlOpener("$WEBSITE_URL#download") },
    )
    platformStore?.let { store ->
        store.listingUrl?.let { listingUrl ->
            LinkListItem(
                title = stringResource(Res.string.settings_rate),
                description = stringResource(Res.string.settings_rate_description, stringResource(store.storeName)),
                icon = painterResource(Res.drawable.ic_star),
                onClick = { urlOpener(listingUrl) },
            )
        }
    }
    if (onShareClicked != null) {
        LinkListItem(
            title = stringResource(Res.string.settings_share_app),
            description = stringResource(Res.string.settings_share_app_description),
            icon = painterResource(Res.drawable.ic_share),
            onClick = onShareClicked,
        )
    }
    if (canAskForDonations) {
        LinkListItem(
            title = stringResource(Res.string.settings_support),
            description = stringResource(Res.string.settings_support_description),
            icon = painterResource(Res.drawable.ic_coffee),
            onClick = { urlOpener("https://buymeacoffee.com/pandulapeter") },
        )
    }
    LinkListItem(
        title = stringResource(Res.string.settings_created_by),
        description = stringResource(Res.string.settings_version, CAMPFIRE_VERSION_NAME),
        icon = painterResource(Res.drawable.ic_campfire),
        onClick = { urlOpener("https://pandulapeter.com/") },
    )
}

@Composable
private fun HelpSection(
    urlOpener: (String) -> Unit,
    processLogTitle: String,
    onProcessLogClicked: () -> Unit,
    onKeyboardShortcutsClicked: () -> Unit,
    onOnboardingClicked: () -> Unit,
) = SettingsSection(title = stringResource(Res.string.settings_group_help)) {
    // The steps the first run opens with, which start from what everything is set to now: help with setting the app up,
    // rather than a preference of its own.
    LinkListItem(
        title = stringResource(Res.string.settings_onboarding),
        description = stringResource(Res.string.settings_onboarding_description),
        icon = painterResource(Res.drawable.ic_campfire),
        onClick = onOnboardingClicked,
    )
    // A desktop or a web build always lists them, the keyboard being how it is used; a phone or a tablet only while one
    // is attached, and the row comes and goes with it.
    AnimatedSettingsRow(isVisible = LocalShowsShortcutHints.current || ShortcutPlatforms.DESKTOP_AND_WEB.includes(appBuild)) {
        LinkListItem(
            title = stringResource(Res.string.keyboard_shortcuts),
            description = stringResource(Res.string.settings_keyboard_shortcuts_description),
            icon = painterResource(Res.drawable.ic_keyboard),
            onClick = onKeyboardShortcutsClicked,
        )
    }
    LinkListItem(
        title = stringResource(Res.string.settings_help),
        description = stringResource(Res.string.settings_help_description),
        icon = painterResource(Res.drawable.ic_bug),
        onClick = { urlOpener("${WEBSITE_URL}support/") },
    )
    LinkListItem(
        title = stringResource(Res.string.settings_git_hub),
        description = stringResource(Res.string.settings_git_hub_description),
        icon = painterResource(Res.drawable.ic_git_hub),
        onClick = { urlOpener("https://github.com/pandulapeter/campfire") },
    )
    LinkListItem(
        title = processLogTitle,
        description = stringResource(Res.string.settings_log_description),
        icon = painterResource(Res.drawable.ic_log),
        onClick = onProcessLogClicked,
    )
}

@Composable
private fun LegalSection(
    urlOpener: (String) -> Unit,
    onOpenSourceLicensesClicked: () -> Unit,
) = SettingsSection(title = stringResource(Res.string.settings_group_legal)) {
    LinkListItem(
        title = stringResource(Res.string.settings_open_source_licenses),
        description = stringResource(Res.string.settings_open_source_licenses_description),
        icon = painterResource(Res.drawable.ic_license),
        onClick = onOpenSourceLicensesClicked,
    )
    LinkListItem(
        title = stringResource(Res.string.settings_privacy_policy),
        description = stringResource(Res.string.settings_privacy_policy_description),
        icon = painterResource(Res.drawable.ic_privacy_policy),
        onClick = { urlOpener("${WEBSITE_URL}privacy/") },
    )
}

/** The name a rating is left under, which is the one thing the app still has to call a store. */
private val Distribution.storeName
    get() = when (this) {
        Distribution.PLAY_STORE -> Res.string.settings_distribution_play_store
        Distribution.APP_STORE -> Res.string.settings_distribution_app_store
        Distribution.MAC_APP_STORE -> Res.string.settings_distribution_mac_app_store
        Distribution.MICROSOFT_STORE -> Res.string.settings_distribution_microsoft_store
    }

private const val WEBSITE_HOST = "campfire-songbook.com"

private const val WEBSITE_URL = "https://$WEBSITE_HOST/"
