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

import com.pandulapeter.campfire.presentation.ui.sync.SyncConsentOrigin

// The app outlives every consent page here, so the screen it was opened from is still the one on top.
internal actual fun keepSyncConsentOrigin(origin: SyncConsentOrigin) = Unit

internal actual fun takeSyncConsentOrigin(): SyncConsentOrigin? = null
