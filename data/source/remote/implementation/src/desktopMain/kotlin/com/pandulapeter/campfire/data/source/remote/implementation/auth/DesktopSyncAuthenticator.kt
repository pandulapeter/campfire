/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.SystemBrowser
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthenticator
import org.koin.core.annotation.Single

/** The desktop's authenticator: a [LoopbackRedirectListener] on the port registered with the service. */
@Single
internal class DesktopSyncAuthenticator(
    systemBrowser: SystemBrowser,
    logger: Logger,
    redirect: OAuthRedirectConfiguration,
) : SyncAuthenticator by LoopbackRedirectListener(systemBrowser = systemBrowser, logger = logger, port = redirect.desktopPort)
