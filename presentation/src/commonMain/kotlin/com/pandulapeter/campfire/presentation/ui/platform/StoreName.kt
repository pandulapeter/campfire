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

import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_distribution_app_store
import com.pandulapeter.campfire.presentation.resources.settings_distribution_mac_app_store
import com.pandulapeter.campfire.presentation.resources.settings_distribution_microsoft_store
import com.pandulapeter.campfire.presentation.resources.settings_distribution_play_store

/** The name a rating is left under, which is the one thing the app still has to call a store. */
internal val Distribution.storeName
    get() = when (this) {
        Distribution.PLAY_STORE -> Res.string.settings_distribution_play_store
        Distribution.APP_STORE -> Res.string.settings_distribution_app_store
        Distribution.MAC_APP_STORE -> Res.string.settings_distribution_mac_app_store
        Distribution.MICROSOFT_STORE -> Res.string.settings_distribution_microsoft_store
    }
