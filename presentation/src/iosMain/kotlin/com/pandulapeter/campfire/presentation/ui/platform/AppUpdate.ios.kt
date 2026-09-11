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

/**
 * Apple ships nothing like Play's in-app updates: there is no API that tells an app a newer build is on the store,
 * and `SKStoreProductViewController` only shows a product page once something already knows to open one. The one
 * way to find out is to ask Apple's public lookup endpoint for the published version and compare it, and that would
 * put a second thing in the app that reaches the network - see the Sync section of the root CLAUDE.md for why that
 * is worth more than the hint would be. iOS also updates apps by itself unless the user turns that off.
 */
@Composable
internal actual fun rememberAppUpdateController(): AppUpdateController = NoAppUpdates
