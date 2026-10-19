/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.sync

interface RestoreSyncUseCase {

    /**
     * Picks sync back up at start up: reads the stored account, and finishes an authorization the app was closed in
     * the middle of. Runs a first sync if that leaves it connected - straight away, since the library the app opens on
     * should be the one in the cloud folder as soon as possible, and the edits that the other automatic runs wait out
     * have not started yet.
     *
     * @param onReturnedFromAuthorization Called as soon as this start up turns out to be the answer to a consent page
     *   the app had been sent away to, which happens on the web, and on Android when the process was reclaimed behind
     *   the browser - before the token exchange, which a bad network can stretch past a minute, and whichever way the
     *   service answers. It says the user is coming back from connecting an account, and so should be shown the screen
     *   they started that from rather than the one the app opens on; that screen shows the connection in progress and
     *   then its outcome.
     */
    suspend operator fun invoke(onReturnedFromAuthorization: () -> Unit)
}
