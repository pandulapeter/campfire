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
import com.pandulapeter.campfire.presentation.resources.ic_bug
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.resources.ic_coffee
import com.pandulapeter.campfire.presentation.resources.ic_git_hub
import com.pandulapeter.campfire.presentation.resources.ic_phone
import com.pandulapeter.campfire.presentation.resources.ic_privacy_policy
import com.pandulapeter.campfire.presentation.resources.ic_star
import com.pandulapeter.campfire.presentation.resources.settings_created_by
import com.pandulapeter.campfire.presentation.resources.settings_distribution_app_store
import com.pandulapeter.campfire.presentation.resources.settings_distribution_mac_app_store
import com.pandulapeter.campfire.presentation.resources.settings_distribution_microsoft_store
import com.pandulapeter.campfire.presentation.resources.settings_distribution_play_store
import com.pandulapeter.campfire.presentation.resources.settings_distributions_all
import com.pandulapeter.campfire.presentation.resources.settings_distributions_all_description
import com.pandulapeter.campfire.presentation.resources.settings_git_hub
import com.pandulapeter.campfire.presentation.resources.settings_git_hub_description
import com.pandulapeter.campfire.presentation.resources.settings_help
import com.pandulapeter.campfire.presentation.resources.settings_help_description
import com.pandulapeter.campfire.presentation.resources.settings_privacy_policy
import com.pandulapeter.campfire.presentation.resources.settings_privacy_policy_description
import com.pandulapeter.campfire.presentation.resources.settings_rate
import com.pandulapeter.campfire.presentation.resources.settings_rate_description
import com.pandulapeter.campfire.presentation.resources.settings_support
import com.pandulapeter.campfire.presentation.resources.settings_support_description
import com.pandulapeter.campfire.presentation.resources.settings_version
import com.pandulapeter.campfire.presentation.ui.components.LinkListItem
import com.pandulapeter.campfire.presentation.ui.platform.Distribution
import com.pandulapeter.campfire.presentation.ui.platform.canAskForDonations
import com.pandulapeter.campfire.presentation.ui.platform.platformStore
import org.jetbrains.compose.resources.painterResource

/**
 * What the app is and where it lives: campfire-songbook.com is its home page. Every build of Campfire is listed there,
 * which is the whole of what the app says about the other platforms, since a page can be kept up to date without a
 * release and it is the one place App Review has nothing to say about; and its support page answers the common
 * questions and says how to reach the author, by email or on GitHub. GitHub comes after those, as the source code and
 * the issue tracker, since that is what it is to somebody who has never used it. The row that names the author is the
 * link to the author's own site, since that is what a name with a link on it is expected to lead to.
 *
 * The rating row leads to the store of the platform the app is running on, and only once that listing exists. It
 * says "rate", never "get it from the store": a store page has an install button on it, and somebody who has the
 * direct download would end up with a second copy of the app, sandboxed, with a library of its own.
 */
@Composable
internal fun AboutSection(
    urlOpener: (String) -> Unit,
) = SettingsSection {
    LinkListItem(
        title = stringResource(Res.string.settings_distributions_all),
        description = stringResource(Res.string.settings_distributions_all_description),
        icon = painterResource(Res.drawable.ic_phone),
        onClick = { urlOpener("$WEBSITE_URL#download") },
    )
    LinkListItem(
        title = stringResource(Res.string.settings_help),
        description = stringResource(Res.string.settings_help_description),
        icon = painterResource(Res.drawable.ic_bug),
        onClick = { urlOpener("${WEBSITE_URL}support/") },
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
    LinkListItem(
        title = stringResource(Res.string.settings_privacy_policy),
        description = stringResource(Res.string.settings_privacy_policy_description),
        icon = painterResource(Res.drawable.ic_privacy_policy),
        onClick = { urlOpener("${WEBSITE_URL}privacy/") },
    )
    LinkListItem(
        title = stringResource(Res.string.settings_git_hub),
        description = stringResource(Res.string.settings_git_hub_description),
        icon = painterResource(Res.drawable.ic_git_hub),
        onClick = { urlOpener("https://github.com/pandulapeter/campfire") },
    )
    LinkListItem(
        title = stringResource(Res.string.settings_created_by),
        description = stringResource(Res.string.settings_version, CAMPFIRE_VERSION_NAME),
        icon = painterResource(Res.drawable.ic_campfire),
        onClick = { urlOpener("https://pandulapeter.com/") },
    )
    if (canAskForDonations) {
        LinkListItem(
            title = stringResource(Res.string.settings_support),
            description = stringResource(Res.string.settings_support_description),
            icon = painterResource(Res.drawable.ic_coffee),
            onClick = { urlOpener("https://buymeacoffee.com/pandulapeter") },
        )
    }
}

/** The name a rating is left under, which is the one thing the app still has to call a store. */
private val Distribution.storeName
    get() = when (this) {
        Distribution.PLAY_STORE -> Res.string.settings_distribution_play_store
        Distribution.APP_STORE -> Res.string.settings_distribution_app_store
        Distribution.MAC_APP_STORE -> Res.string.settings_distribution_mac_app_store
        Distribution.MICROSOFT_STORE -> Res.string.settings_distribution_microsoft_store
    }

private const val WEBSITE_URL = "https://campfire-songbook.com/"
