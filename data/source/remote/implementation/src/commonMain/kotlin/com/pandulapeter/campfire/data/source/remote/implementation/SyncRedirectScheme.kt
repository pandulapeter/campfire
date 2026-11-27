/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation

/**
 * The custom scheme a build's sign-in comes back on, which the shell picks by the build it is and hands to the graph
 * (`startCampfireDependencyGraph`). Only Android's debug build has a scheme of its own, `campfire-debug`: with a store
 * install beside it, both claiming `campfire://oauth` would make the browser's return a chooser, and the wrong pick would
 * lose the sign-in. iOS hands the redirect to the app that opened the session whatever else claims the scheme, the desktop
 * is called back on its loopback port, and the web on its own page, so they keep [Default].
 *
 * Each scheme is a redirect URI registered with Dropbox, and the Android manifest's sign-in filter declares the same one
 * (`syncRedirectScheme` in `app/android/build.gradle.kts`).
 */
class SyncRedirectScheme(internal val value: String) {

    internal val redirect get() = CAMPFIRE_OAUTH_REDIRECT.copy(scheme = value)

    companion object {
        val Default = SyncRedirectScheme(CAMPFIRE_OAUTH_REDIRECT.scheme)
    }
}
