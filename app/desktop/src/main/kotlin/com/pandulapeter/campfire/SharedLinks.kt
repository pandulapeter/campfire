/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import com.pandulapeter.campfire.data.model.share.SharedLink
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Every Campfire link the operating system asks Campfire to open: the landing page's Open in Campfire, which is a
 * `campfire://s/` address. Windows and Linux start the application with it as an argument, macOS sends an Apple event
 * instead (`OpenedFiles.listenForSystemRequests`), and a second instance hands its arguments over to this one; all of
 * them end in [open].
 *
 * A channel for the same reason as [OpenedFiles]: the first link arrives before the window exists, and is imported once.
 */
internal object SharedLinks {

    private val requests = Channel<SharedLink>(Channel.UNLIMITED)

    val links: Flow<SharedLink> = requests.receiveAsFlow()

    /** Safe to call from any thread, and before the window exists. */
    fun open(link: SharedLink) {
        requests.trySend(link)
    }
}
