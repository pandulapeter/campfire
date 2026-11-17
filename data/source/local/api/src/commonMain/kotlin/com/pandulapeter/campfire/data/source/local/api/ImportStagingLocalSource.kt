/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

/**
 * The staging folder an import receives and unpacks into before anything reaches the library. Each process is a
 * session of its own, and every file in the folder carries the session that wrote it.
 */
interface ImportStagingLocalSource {

    /**
     * Deletes every staged file an earlier process left behind (a crash, a killed process, a closed tab), never one this
     * process has written, so it is safe whenever it runs. Called once on launch; never throws but cancellation.
     */
    suspend fun deleteOtherSessions()
}
