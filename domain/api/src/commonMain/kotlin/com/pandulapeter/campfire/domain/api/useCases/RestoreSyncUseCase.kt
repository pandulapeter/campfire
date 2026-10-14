/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

interface RestoreSyncUseCase {

    /**
     * Picks sync back up at start up: reads the stored account, and finishes an authorization the app was closed in
     * the middle of. Runs a first sync if that leaves it connected - straight away, since the library the app opens on
     * should be the one in the cloud folder as soon as possible, and the edits that the other automatic runs wait out
     * have not started yet.
     *
     * @return Whether this start up was the answer to a consent page the app had been sent away to, which happens
     *   on the web, and on Android when the process was reclaimed behind the browser. True either way the service answered: it says the user is coming back from connecting
     *   an account, and so should be shown the screen they started that from rather than the one the app opens on.
     */
    suspend operator fun invoke(): Boolean
}
